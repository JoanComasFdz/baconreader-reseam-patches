// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.links

import app.reseam.patch.Type
import app.reseam.patch.after
import app.reseam.patch.before
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.point
import app.reseam.patch.settings.section
import com.joancomasfdz.baconreader.openlinks.bindings.OpenLinks
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.joancomasfdz.baconreader.settings.LinkSettings
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.SettingsPages
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings

private const val LINK_PROCESSOR = "com.onelouder.baconreader.LinkProcessor"
private const val LINK = "com.onelouder.baconreader.reddit.Link"
private const val REDDIT_SPANNER = "com.onelouder.baconreader.parser.RedditSpanner"
private const val CLICKABLE_LINK_SPAN = "com.onelouder.baconreader.ClickableLinkSpan"
private const val ELEMENT = "com.onelouder.baconreader.parser.RedditParser\$Element"

// LinkProcessor opens a web page in BaconReader's own browser, or in the user's browser when
// "Open links inside app" is off. This sends a web page to the user's browser either way.
val openWebPagesInBrowser =
    patch(BaconReaderStrings.patchOpenWebPagesInBrowser) {
        description(BaconReaderStrings.patchOpenWebPagesInBrowserDescription)
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        settings(
            baconReaderSettings,
            section(
                SettingsPages.Links,
                BaconReaderStrings.sectionOpenWebPages,
                LinkSettings.openWebPagesInBrowser,
            ),
        )

        execute {
            // The link button of a post's bar has no label for screen readers.
            buttonCreated.after { OpenLinks.labelLinkButton(result, param(1)) }

            // A post's header and its link button open through getBrowserIntent instead.
            browserIntent.before {
                val intent = OpenLinks.webLinkIntent(param(0), param(1))
                whenNotNull(intent) { returnValue(intent) }
            }

            // The link next to a post's title starts LinkProcessor's intent only when it opens
            // BaconReader's browser, so it would drop the intents above and do nothing.
            linkSpanClick.before {
                whenTrue(OpenLinks.openWebLink(param(0), thisObject.field(spanLink))) {
                    returnVoid()
                }
            }

            // A link in a text opens through LinkProcessor only with "Open links inside app" on, so
            // an
            // image there would open in the browser.
            webSpannerLink.after {
                capture("inApp").assign(OpenLinks.mediaOpensInApp(capture("inApp"), param(1)))
            }

            getIntent.before {
                val intent =
                    OpenLinks.webPageIntent(
                        thisObject.field(processorContext),
                        thisObject.field(processorUrl),
                        thisObject.field(processorForceBrowser),
                    )
                whenNotNull(intent) { returnValue(intent) }
            }
        }
    }

private val processor = klass(LINK_PROCESSOR)
private val getIntent = processor.method("getIntent") { params() }
private val browserIntent =
    processor.method("getBrowserIntent") { params("android.content.Context", LINK) }
private val processorContext = processor.field("context")
private val processorUrl = processor.field("url")
private val processorForceBrowser = processor.field("forceBrowser")

// Where the text of posts, comments and messages reads "Open links inside app" for a link.
private val webSpannerLink =
    klass(REDDIT_SPANNER)
        .method("appendElementInternal") { params("android.text.SpannableStringBuilder", ELEMENT) }
        .point("webSpannerLink") {
            field {
                owner(REDDIT_SPANNER)
                name("internalBrowser")
            }
        }
        .captureAs("inApp")

private val linkSpan = klass(CLICKABLE_LINK_SPAN)
private val linkSpanClick = linkSpan.method("onClick") { params("android.view.View") }
private val spanLink = linkSpan.field("link")

// Makes a button of a post's bar: its image, the action it's tagged with, and its view id.
private val buttonCreated =
    klass("com.onelouder.baconreader.controlbar.ControlbarBase").method("createButton") {
        params(Type.Int, Type.Int, Type.Int)
    }
