// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.media

import app.reseam.patch.before
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.point
import app.reseam.patch.settings.section
import com.joancomasfdz.baconreader.detailvideo.bindings.DetailVideo
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.joancomasfdz.baconreader.settings.DetailVideoSettings
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.SettingsPages
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings

private const val POST_MEDIA_HANDLER = "com.onelouder.baconreader.PostMediaHandler"
private const val VIDEO_HANDLER = "com.onelouder.baconreader.media.VideoHandler"
private const val PLAYER = "com.google.android.exoplayer2.SimpleExoPlayer"

// BaconReader starts every video as soon as the detail view of a post shows it, on mobile data too,
// and has no setting to wait for a tap.
val videoAutoplayInDetailView =
    patch("Video autoplay in detail view") {
        description(
            "Adds a setting to stop videos, such as v.redd.it videos, from playing by themselves in a post's detail view: they load and wait for a tap on play."
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
            // The app creates the player playing, then sets it to play when the post is on screen.
            createdPlayerPlays.before {
                capture("playWhenReady")
                    .assign(
                        DetailVideo.playWhenReady(
                            thisObject.field(handlerListener),
                            capture("videoHandler"),
                            capture("playWhenReady"),
                        )
                    )
            }

            // The post coming on screen, and the app coming back, resume the video unless the user
            // paused it.
            resumeFromPlay.before {
                whenTrue(
                    DetailVideo.holdsVideo(
                        thisObject.field(handlerListener),
                        thisObject.field(handlerVideo),
                    )
                ) {
                    returnVoid()
                }
            }

            // The play and pause button, and restart. After the user's first one, the app's own
            // rules apply.
            toggleVideoPlaying.before { DetailVideo.onUserControl(thisObject) }
            restartVideo.before { DetailVideo.onUserControl(thisObject) }
        }
    }

private val handler = klass(POST_MEDIA_HANDLER)
private val handlerListener = handler.field("listener")
private val handlerVideo = handler.field("videoHandler")

// The call after which the player plays only if the post is the one on screen.
private val createdPlayerPlays =
    handler
        .method("onCreateVideo") { params(PLAYER) }
        .point("enableRepeatMode") {
            invokeVirtual {
                owner(VIDEO_HANDLER)
                name("enableRepeatMode")
            }
        }
        .captureArgumentAs("videoHandler", 0)
        .captureArgumentAs("playWhenReady", 1)

// Resumes the video, unless the user paused it.
private val resumeFromPlay =
    handler
        .method("play") { params() }
        .point("resumeVideo") {
            invokeVirtual {
                owner(VIDEO_HANDLER)
                name("resumeVideo")
            }
        }

private val toggleVideoPlaying = klass(VIDEO_HANDLER).method("toggleVideoPlaying") { params() }
private val restartVideo = klass(VIDEO_HANDLER).method("restartVideo") { params() }
