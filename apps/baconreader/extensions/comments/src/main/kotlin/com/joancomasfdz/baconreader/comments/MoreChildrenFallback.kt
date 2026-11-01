// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("MoreChildrenFallback")

package com.joancomasfdz.baconreader.comments

import android.content.Context
import android.util.Log
import app.reseam.runtime.Export
import com.onelouder.baconreader.connectivity.Tasks
import com.onelouder.baconreader.data.ThreadKey
import com.onelouder.baconreader.data.ThreadManager
import com.onelouder.baconreader.reddit.Comment
import com.onelouder.baconreader.reddit.Listing
import com.onelouder.baconreader.reddit.More
import com.onelouder.baconreader.reddit.MoreChildrenResponse
import com.onelouder.baconreader.reddit.RedditApi
import com.onelouder.baconreader.reddit.Thing
import com.onelouder.baconreader.reddit.ThingData

private const val TAG = "FixLoadMoreComments"

/** The single-comment threads a top-level "more" loads at a time. */
private const val BATCH = 50

/** Comments a thread asks for: a parent comment's replies, or one top-level comment's. */
private const val THREAD_LIMIT = 500

/**
 * The "more" ThreadManager.loadMore is loading, and its thread's key, for getMoreChildren, which it
 * calls right away.
 */
private var loading: More? = null
private var loadingThread: ThreadKey? = null

/**
 * Called at the start of ThreadManager.loadMore, which is synchronized: the thread and the "more"
 * it loads.
 */
@Export
fun loading(threadKey: ThreadKey?, more: More?) {
    loadingThread = threadKey
    loading = more
}

/**
 * Called at the start of RedditApi.getMoreChildren(context, linkId, sort, children, listener): the
 * listener to use instead, which gets Reddit's answer as before, unless Reddit refuses
 * api/morechildren (403). Then the same comments come from their threads, which Reddit still
 * serves, and the listener gets them as api/morechildren would have sent them.
 */
@Export
fun guard(
    context: Context?,
    linkId: String?,
    sort: String?,
    listener: Tasks.OnCompleteListener<MoreChildrenResponse>?,
): Tasks.OnCompleteListener<MoreChildrenResponse>? {
    val more = loading
    val threadKey = loadingThread
    loading = null
    loadingThread = null
    if (context == null || linkId == null || listener == null || more == null) return listener
    return Guarded(context, linkId, sort, more, threadKey, listener)
}

private class Guarded(
    private val context: Context,
    private val linkId: String,
    private val sort: String?,
    private val more: More,
    private val threadKey: ThreadKey?,
    private val app: Tasks.OnCompleteListener<MoreChildrenResponse>,
) : Tasks.OnCompleteListener<MoreChildrenResponse>() {
    override fun processInBackground(response: MoreChildrenResponse) =
        app.processInBackground(response)

    override fun onComplete(response: MoreChildrenResponse) = app.onComplete(response)

    override fun onCancel(error: String?) {
        if (error == null || !error.contains("403")) {
            app.onCancel(error)
            return
        }
        Log.i(TAG, "api/morechildren refused ($error): loading the comments from their threads")
        try {
            val parent = more.parent_id
            if (parent != null && parent.startsWith("t1_"))
                loadFromParent(parent.substring(3), error)
            else loadTopLevel(error)
        } catch (ex: Exception) {
            Log.i(TAG, "Could not load the comments from their threads", ex)
            app.onCancel(error)
        }
    }

    /**
     * A "more" under a comment: the comment's thread holds the comments it hides, its replies or
     * further down, which keep their depth below the comment.
     */
    private fun loadFromParent(parentId: String, error: String) {
        val wanted = more.children.orEmpty().toSet()
        thread(parentId) { listings ->
            val parent =
                listings?.getOrNull(1)?.getChildren(Comment::class.java)?.firstOrNull {
                    it.id == parentId
                }
            if (parent == null) {
                app.onCancel(error)
                return@thread
            }
            val found = ArrayList<Thing>()
            collect(parent.replies.orEmpty(), wanted, found)
            val depth = depthInThread(parentId) ?: parent.depth
            if (found.isNotEmpty()) {
                val by = depth - parent.depth
                if (by != 0) for (thing in found) (thing.data as? Comment)?.let { shiftBy(it, by) }
                deliver(found)
                return@thread
            }
            // Reddit leaves deleted and removed comments out of threads; api/morechildren still
            // named them. Their own threads show those Reddit still has.
            loadEach(more.children.orEmpty().take(BATCH), depth + 1) { comments ->
                deliver(comments.map { thing("t1", it) })
            }
        }
    }

    /**
     * The comments [ids] from their own threads, in their order, at [depth], or none for those
     * Reddit doesn't send.
     */
    private fun loadEach(ids: List<String>, depth: Int, done: (List<Comment>) -> Unit) {
        if (ids.isEmpty()) {
            done(emptyList())
            return
        }
        val loaded = arrayOfNulls<Comment>(ids.size)
        var answered = 0
        ids.forEachIndexed { index, id ->
            thread(id) { listings ->
                loaded[index] =
                    listings?.getOrNull(1)?.getChildren(Comment::class.java)?.firstOrNull {
                        it.id == id
                    }
                loaded[index]?.let { shift(it, depth) }
                answered++
                if (answered == ids.size) done(loaded.filterNotNull())
            }
        }
    }

    /**
     * A "more" of top-level comments: their threads, [BATCH] at a time, in their order, and a
     * "more" for the rest.
     */
    private fun loadTopLevel(error: String) {
        val children = more.children.orEmpty()
        val batch = children.take(BATCH)
        val rest = children.drop(BATCH)
        if (batch.isEmpty()) {
            app.onCancel(error)
            return
        }
        loadEach(batch, 0) { comments ->
            val things = ArrayList<Thing>()
            var count = 0
            for (comment in comments) {
                count += 1 + replyCount(comment)
                things.add(thing("t1", comment))
            }
            if (things.isEmpty() && rest.isEmpty()) {
                app.onCancel(error)
                return@loadEach
            }
            if (rest.isNotEmpty()) {
                val next = More()
                next.id = rest[0]
                next.name = "t1_" + rest[0]
                next.parent_id = more.parent_id
                next.children = ArrayList(rest)
                next.count = maxOf(rest.size, more.count - count)
                things.add(thing("more", next))
            }
            deliver(things)
        }
    }

    /** How deep the comment [id] is in the thread the app shows, or null when it isn't there. */
    private fun depthInThread(id: String): Int? =
        try {
            synchronized(ThreadManager::class.java) {
                val threads =
                    ThreadManager::class
                        .java
                        .getDeclaredField("threads")
                        .apply { isAccessible = true }
                        .get(null) as Map<*, *>
                val thread = threads[threadKey] as ThreadManager.Thread? ?: return null
                thread.replies
                    .orEmpty()
                    .filterIsInstance<Comment>()
                    .firstOrNull { it.id == id }
                    ?.depth
            }
        } catch (ex: Exception) {
            Log.i(TAG, "Could not read the thread's comments", ex)
            null
        }

    /** The thread of one comment of the post, as BaconReader asks for a post's comments. */
    private fun thread(commentId: String, done: (List<Listing>?) -> Unit) {
        RedditApi.getComments(
            context,
            linkId,
            commentId,
            0,
            sort,
            THREAD_LIMIT,
            object : Tasks.OnCompleteListener<List<Listing>>() {
                override fun onComplete(listings: List<Listing>?) {
                    try {
                        done(listings)
                    } catch (ex: Exception) {
                        Log.i(TAG, "Could not load the comments from their threads", ex)
                        app.onCancel("Could not load the comments: $ex")
                    }
                }

                override fun onCancel(error: String?) {
                    Log.i(TAG, "Could not load the thread of $commentId: $error")
                    done(null)
                }
            },
        )
    }

    /** What api/morechildren would have answered, to the app's own listener. */
    private fun deliver(things: List<Thing>) {
        val response = MoreChildrenResponse()
        response.json = MoreChildrenResponse.MoreChildrenResponseJson()
        response.json.errors = ArrayList<Any>()
        response.json.data = MoreChildrenResponse.MoreChildrenResponseData()
        response.json.data.things = things
        app.processInBackground(response)
        app.onComplete(response)
    }
}

private fun thing(kind: String, data: ThingData): Thing {
    val thing = Thing()
    thing.kind = kind
    thing.data = data
    return thing
}

/**
 * Moves a comment, and its replies with it, to [depth]: a thread starts at its own comment, and
 * BaconReader indents a comment by its depth.
 */
/**
 * The topmost of [replies] and their replies that [wanted] names, in order: comments, with their
 * replies, and "more"s that hide some of them.
 */
private fun collect(replies: List<Thing>, wanted: Set<String>, found: MutableList<Thing>) {
    for (reply in replies) {
        when (val data = reply.data) {
            is Comment ->
                if (data.id in wanted) found.add(reply)
                else collect(data.replies.orEmpty(), wanted, found)
            is More -> if (data.children.orEmpty().any { it in wanted }) found.add(reply)
        }
    }
}

private fun shift(data: ThingData?, depth: Int) {
    val comment = data as? Comment ?: return
    val by = depth - comment.depth
    if (by != 0) shiftBy(comment, by)
}

private fun shiftBy(comment: Comment, by: Int) {
    comment.depth += by
    for (reply in comment.replies.orEmpty()) (reply.data as? Comment)?.let { shiftBy(it, by) }
}

private fun replyCount(comment: Comment): Int =
    comment.replies.orEmpty().sumOf { reply ->
        when (val data = reply.data) {
            is Comment -> 1 + replyCount(data)
            is More -> data.count
            else -> 0
        }
    }
