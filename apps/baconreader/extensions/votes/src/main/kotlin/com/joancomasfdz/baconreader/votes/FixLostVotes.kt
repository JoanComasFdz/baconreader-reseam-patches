// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("FixLostVotes")

package com.joancomasfdz.baconreader.votes

import android.os.SystemClock
import app.reseam.runtime.Export
import com.onelouder.baconreader.reddit.Link

// A vote on a post made while its comments load was undone in the post list: the post that comes
// with the comments was requested before the vote, and the app saves it to the list. The votes
// made since are applied to it first. The vote itself reaches Reddit.

// Votes older than this are in any post Reddit sends now.
private const val KEEP_MS = 5 * 60 * 1000L
private const val MAX_VOTES = 50

private class Vote(val direction: Int, val time: Long)

/** Thing name, such as "t3_abc", to the latest vote on it. */
private val votes =
    object : LinkedHashMap<String, Vote>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Vote>?): Boolean =
            size > MAX_VOTES
    }

/** Called at the start of RedditApi.vote(context, direction, name). */
@Export
fun onVote(direction: Int, name: String?) {
    if (name == null) return
    synchronized(votes) { votes[name] = Vote(direction, SystemClock.elapsedRealtime()) }
}

/**
 * Called in ThreadManager.CommentsListener.processInBackground, before the post that came with the
 * comments is saved to the post list.
 */
@Export
fun keepVote(link: Link?) {
    val name = link?.name ?: return
    val vote = synchronized(votes) { votes[name] } ?: return
    if (SystemClock.elapsedRealtime() - vote.time > KEEP_MS) return

    val current =
        when (link.likes) {
            null -> 0
            true -> 1
            false -> -1
        }
    // Reddit already has it.
    if (current == vote.direction) return
    val delta = vote.direction - current
    link.likes = if (vote.direction == 0) null else vote.direction > 0
    link.score += delta
    link.ups += delta
}
