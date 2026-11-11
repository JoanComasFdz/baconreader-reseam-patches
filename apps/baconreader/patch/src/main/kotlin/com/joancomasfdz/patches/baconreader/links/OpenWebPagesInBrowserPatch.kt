// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.links

import app.reseam.patch.before
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.settings.section
import com.joancomasfdz.baconreader.openlinks.bindings.OpenLinks
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.joancomasfdz.baconreader.settings.LinkSettings
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.SettingsPages
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings

private const val LINK_PROCESSOR = "com.onelouder.baconreader.LinkProcessor"

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
private val processorContext = processor.field("context")
private val processorUrl = processor.field("url")
private val processorForceBrowser = processor.field("forceBrowser")
