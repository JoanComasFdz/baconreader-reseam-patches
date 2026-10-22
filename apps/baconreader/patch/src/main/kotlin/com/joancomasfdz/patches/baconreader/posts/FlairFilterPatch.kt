// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.posts

import app.reseam.patch.before
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import com.joancomasfdz.baconreader.flairs.bindings.FlairFilter
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings

// BaconReader can't filter a subreddit by flair. Reddit's search can, and BaconReader already
// loads a feed whose key has a query as a search in the subreddit, so the filter is a search.
val flairFilter =
    patch("Flair filter") {
        description(
            "Adds a Flair button to the sort bar of subreddits, to show only the posts with the chosen flairs."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        dependsOn(baconReaderSettings)

        execute {
            openLinkset.before { FlairFilter.onOpenLinkset(thisObject) }
            addToLinkset.before { FlairFilter.onListingLoaded(param(1)) }
        }
    }

// Opens the feed of MainActivity.linksetKey: on start, and when the subreddit, sort or query
// changes.
private val openLinkset =
    klass("com.onelouder.baconreader.ui.main.MainActivity").method("openLinkset")

// Adds a page of posts from Reddit to a feed. Their flairs are the list for subreddits whose
// flairs aren't public.
private val addToLinkset =
    klass("com.onelouder.baconreader.data.LinksetManager").method("addToLinkset")
