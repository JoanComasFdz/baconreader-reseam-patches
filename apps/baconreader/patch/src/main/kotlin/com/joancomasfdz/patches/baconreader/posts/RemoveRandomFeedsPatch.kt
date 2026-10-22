// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.posts

import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.point
import app.reseam.patch.skipWhen
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM

// Reddit removed r/random and r/myrandom in December 2024, so the Random and My Random feeds never
// load.
val removeRandomFeeds =
    patch("Remove Random feeds") {
        description("Removes Random and My Random, which no longer load, from the feed list.")
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)

        execute {
            randomFeed.skipWhen { bool(true) }
            myRandomFeed.skipWhen { bool(true) }
        }
    }

// Fills the feed list of the post list: Front Page, All, Popular, Random, My Random, Friends and
// the subreddits.
private val resetFeeds = klass("com.onelouder.baconreader.adapters.FeedAdapter").method("reset")

private val randomFeed = addFeed("Random")

private val myRandomFeed = addFeed("My Random")

// items.add(new FeedItem("<feed>", false)), whose result isn't used.
private fun addFeed(feed: String) =
    resetFeeds.point("${feed}Feed") { string(feed) }.next { invokeVirtual { name("add") } }
