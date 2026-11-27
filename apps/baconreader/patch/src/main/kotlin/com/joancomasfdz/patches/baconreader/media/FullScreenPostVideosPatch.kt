// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.media

import app.reseam.patch.after
import app.reseam.patch.before
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.settings.section
import com.joancomasfdz.baconreader.postvideo.bindings.PostVideoFullScreen
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.joancomasfdz.baconreader.settings.PostVideoFullScreenSettings
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.SettingsPages
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings

private const val POST_MEDIA_HANDLER = "com.onelouder.baconreader.PostMediaHandler"

// BaconReader's player in a post's detail view has no full screen: a video only shows as wide as
// the post, and the screen doesn't turn for a video wider than high.
val fullScreenPostVideos =
    patch(BaconReaderStrings.patchFullScreenPostVideos) {
        description(BaconReaderStrings.patchFullScreenPostVideosDescription)
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        settings(
            baconReaderSettings,
            section(
                SettingsPages.Images,
                BaconReaderStrings.sectionPostVideoFullScreen,
                PostVideoFullScreenSettings.postVideoFullScreenButton,
            ),
        )

        execute {
            // The full screen's player view, made in code, so the e2e tests find it.
            resources.addId("baconreader_patches_video_full_screen")

            onCreateVideo.after {
                PostVideoFullScreen.addButton(
                    thisObject.field(handlerListener),
                    thisObject.field(handlerVideoView),
                    param(0),
                )
            }
            destroyVideo.before { PostVideoFullScreen.release(thisObject.field(handlerVideoView)) }
        }
    }

private val handler = klass(POST_MEDIA_HANDLER)

// Binds the player to the post's player view and wires its controls, once the video plays.
private val onCreateVideo = handler.method("onCreateVideo")

// Releases the post's player, when the post closes or its media reloads.
private val destroyVideo = handler.method("destroyVideo")
private val handlerListener = handler.field("listener")
private val handlerVideoView = handler.field("videoView")
