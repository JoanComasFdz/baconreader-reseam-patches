// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.posts

import app.reseam.patch.Type
import app.reseam.patch.before
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.point
import com.joancomasfdz.baconreader.votes.bindings.FixLostVotes
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM

// Opening a post loads its comments together with a fresh copy of the post, which the app saves
// to the post list. A vote made while they load is missing from that copy, requested before the
// vote, so the post list shows the post as not voted. Reddit still has the vote.
val fixLostVotes =
    patch("Fix lost post votes") {
        description(
            "Keeps votes on a post in the post list when they're made while its comments load."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)

        execute {
            vote.before { FixLostVotes.onVote(param(1), param(2)) }
            savePost.before { FixLostVotes.keepVote(capture("link")) }
        }
    }

private val vote = klass("com.onelouder.baconreader.reddit.RedditApi").method("vote")

// The post that came with the comments, saved to the post list.
private val savePost =
    klass("com.onelouder.baconreader.data.ThreadManager\$CommentsListener")
        .method("processInBackground") { params(Type.List) }
        .point("savePost") {
            invokeStatic {
                owner("com.onelouder.baconreader.data.LinksetManager")
                name("update")
            }
        }
        .captureArgumentAs("link", 0)
