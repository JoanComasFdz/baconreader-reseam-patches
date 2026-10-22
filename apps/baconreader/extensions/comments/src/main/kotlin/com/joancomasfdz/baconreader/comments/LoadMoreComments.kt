// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("LoadMoreComments")

package com.joancomasfdz.baconreader.comments

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.common.ErrorDialog
import com.joancomasfdz.baconreader.settings.LoadMoreCommentsSettings
import com.onelouder.baconreader.adapters.CommentsAdapter
import com.onelouder.baconreader.connectivity.Tasks
import com.onelouder.baconreader.data.ThreadKey
import com.onelouder.baconreader.data.ThreadManager
import com.onelouder.baconreader.reddit.More
import java.util.Collections
import java.util.WeakHashMap

/**
 * Fewer "load more comments" in posts. Reddit returns about 200 comments by default and puts the
 * replies that don't fit behind "load more comments (N replies)", often for one or two low-ranked
 * replies. The thread is requested with the chosen number of comments, 200 to 500, and the "load
 * more" of up to the chosen number of replies are loaded right after the thread.
 */
private object Loader {
    const val TAG = "LoadMoreComments"

    /** Reddit's default, and its maximum. */
    const val MIN_LIMIT = 200
    const val MAX_LIMIT = 500

    /** The safety limit of automatic requests per thread. */
    const val MAX_AUTO_REQUESTS = 30

    val main = Handler(Looper.getMainLooper())

    // Main thread only.

    /** The "more" items already loaded or tried. A reload of the thread has new ones. */
    val tried: MutableSet<Any> = Collections.newSetFromMap(WeakHashMap())

    /** Per loaded thread, by its first comment: the automatic requests made. */
    val requests = WeakHashMap<Any, Int>()

    /** Comment lists loading a "more" automatically. */
    val running = WeakHashMap<Any, Boolean>()
}

/**
 * The limit of RedditApi.getComments' request, which is 0 for Reddit's default of about 200: the
 * "Comments to load" setting instead.
 */
@Export fun commentLimit(limit: Int): Int = if (limit > 0) limit else limit()

/** The setting, within Reddit's limits, or the default when it isn't a number. */
private fun limit(): Int {
    val value =
        LoadMoreCommentsSettings.commentLimit.value.trim().toIntOrNull()
            ?: LoadMoreCommentsSettings.commentLimit.default.toInt()
    return value.coerceIn(Loader.MIN_LIMIT, Loader.MAX_LIMIT)
}

private fun maxReplies() = LoadMoreCommentsSettings.autoLoadMoreComments.value.toIntOrNull() ?: 0

/** Loads the small "load more comments", whenever the comment list gets its items. */
@Export
fun onItemsProcessed(adapter: CommentsAdapter?) {
    if (adapter == null || maxReplies() <= 0 || Loader.running[adapter] == true) return
    // After the list has shown the new items.
    Loader.main.post { loadNext(adapter) }
}

private fun loadNext(adapter: CommentsAdapter) {
    try {
        val maxReplies = maxReplies()
        if (maxReplies <= 0 || Loader.running[adapter] == true) return
        // The list loads its last "more" by itself when scrolled to the end.
        if (field(adapter, "loading") == true) {
            Loader.main.postDelayed({ loadNext(adapter) }, 500)
            return
        }
        val threadKey = field(adapter, "threadKey") as ThreadKey?
        var more: More? = null
        var firstComment: Any? = null
        synchronized(ThreadManager::class.java) {
            val threads = staticField(ThreadManager::class.java, "threads") as Map<*, *>
            val thread = threads[threadKey] as ThreadManager.Thread? ?: return
            val states = thread.states
            val replies = ArrayList<Any?>(thread.replies)
            firstComment = replies.firstOrNull()
            more =
                replies.filterIsInstance<More>().firstOrNull {
                    // "Continue this thread" has no children: it's another page.
                    !it.children.isNullOrEmpty() &&
                        it.count <= maxReplies &&
                        // Not loaded or loading already.
                        it !in Loader.tried &&
                        states?.containsKey(it.id) != true
                }
        }
        val next = more ?: return
        val thread = firstComment ?: return
        val requests = Loader.requests[thread] ?: 0
        if (requests >= Loader.MAX_AUTO_REQUESTS) return
        Loader.requests[thread] = requests + 1
        Loader.tried.add(next)
        Loader.running[adapter] = true

        ThreadManager.loadMore(
            field(adapter, "activity") as Context?,
            adapter,
            threadKey,
            next,
            object : Tasks.OnCompleteListener<Any?>() {
                override fun onComplete(data: Any?) {
                    Loader.running.remove(adapter)
                    try {
                        // Shows the comments, and processData continues with the next "more".
                        adapter.requery()
                    } catch (ex: Exception) {
                        Log.i(Loader.TAG, "Could not show the loaded comments", ex)
                    }
                }

                override fun onCancel(error: String?) {
                    Loader.running.remove(adapter)
                    Log.i(Loader.TAG, "Could not load more comments: $error")
                }
            },
        )
    } catch (ex: Exception) {
        Loader.running.remove(adapter)
        ErrorDialog.report(Loader.TAG, "Could not load more comments", ex)
    }
}

// The adapter's activity, loading state and thread key, and ThreadManager's threads, are private
// or protected in BaconReader.
private fun field(target: Any, name: String): Any? {
    var type: Class<*>? = target.javaClass
    while (type != null) {
        try {
            return type.getDeclaredField(name).apply { isAccessible = true }.get(target)
        } catch (ignored: NoSuchFieldException) {}
        type = type.superclass
    }
    throw NoSuchFieldException(name)
}

private fun staticField(type: Class<*>, name: String): Any? =
    type.getDeclaredField(name).apply { isAccessible = true }.get(null)
