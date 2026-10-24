// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.media

import app.reseam.patch.Type
import app.reseam.patch.before
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.settings.section
import com.joancomasfdz.baconreader.detailvideo.bindings.DetailVideo
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.joancomasfdz.baconreader.settings.DetailVideoSettings
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.SettingsPages
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings

private const val POST_MEDIA_HANDLER = "com.onelouder.baconreader.PostMediaHandler"

// BaconReader starts every video as soon as the detail view of a post shows it, on mobile data too,
// and has no setting to wait for a tap.
val videoAutoplayInDetailView =
    patch("Video autoplay in detail view") {
        description(
            "Adds a setting to stop videos, such as v.redd.it videos, from loading and playing by themselves in a post's detail view: the post shows its preview image, and a tap on it plays the video."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        settings(
            baconReaderSettings,
            section(
                SettingsPages.Images,
                BaconReaderStrings.sectionDetailVideo,
                DetailVideoSettings.autoplayVideosInDetailView,
            ),
        )

        execute {
            displayVideo.before {
                whenTrue(
                    DetailVideo.deferVideo(
                        thisObject.field(handlerListener),
                        thisObject,
                        thisObject.field(handlerLink),
                        thisObject.field(handlerFlipper),
                        thisObject.field(handlerVideo),
                        param(0),
                        param(1),
                    )
                ) {
                    returnVoid()
                }
            }
        }
    }

private val handler = klass(POST_MEDIA_HANDLER)

// Shows the video page and starts the player, once the app knows the video's URL.
private val displayVideo =
    handler.method("displayVideo") { params("android.content.Context", Type.String) }
private val handlerListener = handler.field("listener")
private val handlerLink = handler.field("link")
private val handlerFlipper = handler.field("viewFlipper")
private val handlerVideo = handler.field("videoHandler")
