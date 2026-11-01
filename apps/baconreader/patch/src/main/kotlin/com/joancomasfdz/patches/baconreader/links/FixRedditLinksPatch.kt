// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.links

import app.reseam.patch.before
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import com.joancomasfdz.baconreader.redditlinks.bindings.RedditLinks
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM

private const val LINK_PROCESSOR = "com.onelouder.baconreader.LinkProcessor"
private const val LINK_DISPATCHER = "com.onelouder.baconreader.LinkDispatcher"

// LinkProcessor decides where a tapped Reddit link opens, by the shape of its path, and opens
// every path it doesn't know in its browser. It predates Reddit's share links,
// /r/<sub>/s/<code>, which say what they open only through a redirect. It also opens
// /comments/<post> and /gallery/<post> in its browser, and a post on a user's profile,
// /user/<user>/comments/<post>, as the profile.
val fixRedditLinks =
    patch("Fix Reddit links") {
        description(
            "Opens Reddit's share links, and links to galleries, to posts without their subreddit and to posts on a user's profile, in the app instead of its browser."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)

        execute {
            getIntent.before {
                val intent =
                    RedditLinks.intent(
                        thisObject.field(processorContext),
                        thisObject.field(processorUrl),
                        thisObject.field(processorForceBrowser),
                    )
                whenNotNull(intent) { returnValue(intent) }
            }

            // A share link comes back to LinkDispatcher, which waits for its redirect.
            processing.before {
                whenTrue(RedditLinks.dispatch(thisObject, param(0))) { returnTrue() }
            }
        }
    }

private val processor = klass(LINK_PROCESSOR)
private val getIntent = processor.method("getIntent") { params() }
private val processorContext = processor.field("context")
private val processorUrl = processor.field("url")
private val processorForceBrowser = processor.field("forceBrowser")

private val processing =
    klass(LINK_DISPATCHER).method("processing") { params("android.content.Intent") }
