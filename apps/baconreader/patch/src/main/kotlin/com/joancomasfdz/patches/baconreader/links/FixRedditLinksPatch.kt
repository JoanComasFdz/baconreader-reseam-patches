// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.links

import app.reseam.patch.after
import app.reseam.patch.before
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.point
import com.joancomasfdz.baconreader.redditlinks.bindings.RedditLinks
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM

private const val LINK_PROCESSOR = "com.onelouder.baconreader.LinkProcessor"
private const val LINK_DISPATCHER = "com.onelouder.baconreader.LinkDispatcher"
private const val CLICKABLE_LINK_SPAN = "com.onelouder.baconreader.ClickableLinkSpan"
private const val LINK = "com.onelouder.baconreader.reddit.Link"
private const val REDDIT_SPANNER = "com.onelouder.baconreader.parser.RedditSpanner"
private const val ELEMENT = "com.onelouder.baconreader.parser.RedditParser\$Element"
private const val DETAIL_LINK_HELPER =
    "com.onelouder.baconreader.adapters.DetailLinkFragment\$DetailLinkHelper"

// LinkProcessor decides where a tapped Reddit link opens, by the shape of its path, and opens
// every path it doesn't know in its browser. It predates Reddit's share links,
// /r/<sub>/s/<code>, which say what they open only through a redirect. It also opens
// /comments/<post> and /gallery/<post> in its browser, and a post on a user's profile,
// /user/<user>/comments/<post>, as the profile. A video's link, v.redd.it/<video>, opens in its
// media viewer, which looks the post up through www.reddit.com/video/<video>, and Reddit refuses
// that. It looks a gallery's post up through www.reddit.com/<post>, which no longer redirects.
val fixRedditLinks =
    patch(BaconReaderStrings.patchFixRedditLinks) {
        description(BaconReaderStrings.patchFixRedditLinksDescription)
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

            // The link next to a post's title starts LinkProcessor's intent only when it opens
            // the browser, so it dropped the intents above and did nothing.
            linkSpanClick.before {
                whenTrue(RedditLinks.open(param(0), thisObject.field(spanLink))) { returnVoid() }
            }

            // A post's thumbnail, in the list and in the post's header, and the link next to its
            // title, open the gallery or Reddit video the post carries in the app's media viewer,
            // through LinkProcessor's intent: the intents above would open the post itself again.
            browserIntent.before { RedditLinks.keepAppMedia(param(1)) }
            headerOpen.before { RedditLinks.keepAppMedia(param(0)) }

            // With "Open links inside app" off, a link in a text opens in the browser, Reddit's
            // too.
            listOf(spannerLink, spannerRelativeLink).forEach { point ->
                point.after {
                    capture("inApp").assign(RedditLinks.opensInApp(capture("inApp"), param(1)))
                }
            }

            // The media viewer looks up a post it has only the address of through a redirect
            // Reddit no longer sends.
            mediaLookup.before {
                val post = RedditLinks.mediaPost(thisObject)
                whenNotNull(post) { returnValue(post) }
            }
        }
    }

private val processor = klass(LINK_PROCESSOR)
private val getIntent = processor.method("getIntent") { params() }
private val processorContext = processor.field("context")
private val processorUrl = processor.field("url")
private val processorForceBrowser = processor.field("forceBrowser")
private val browserIntent =
    processor.method("getBrowserIntent") { params("android.content.Context", LINK) }

// Opens the media of the post shown, from the thumbnail in its header.
private val headerOpen = klass(DETAIL_LINK_HELPER).method("onOpen") { params(LINK) }

private val mediaLookup =
    klass("com.onelouder.baconreader.reddit.RedditMediaHandler\$2").method("runTask") {
        params()
        returns(LINK)
    }

// Where the text of posts, comments and messages reads "Open links inside app" for a link, and for
// a
// relative one, /r/<sub>.
private val spannerLink =
    klass(REDDIT_SPANNER)
        .method("appendElementInternal") { params("android.text.SpannableStringBuilder", ELEMENT) }
        .point("spannerLink") {
            field {
                owner(REDDIT_SPANNER)
                name("internalBrowser")
            }
        }
        .captureAs("inApp")
private val spannerRelativeLink =
    klass(REDDIT_SPANNER)
        .method("appendRelativeLink") {
            params("android.text.SpannableStringBuilder", ELEMENT, "java.lang.String")
        }
        .point("spannerRelativeLink") {
            field {
                owner(REDDIT_SPANNER)
                name("internalBrowser")
            }
        }
        .captureAs("inApp")

private val processing =
    klass(LINK_DISPATCHER).method("processing") { params("android.content.Intent") }

private val linkSpan = klass(CLICKABLE_LINK_SPAN)
private val linkSpanClick = linkSpan.method("onClick") { params("android.view.View") }
private val spanLink = linkSpan.field("link")
