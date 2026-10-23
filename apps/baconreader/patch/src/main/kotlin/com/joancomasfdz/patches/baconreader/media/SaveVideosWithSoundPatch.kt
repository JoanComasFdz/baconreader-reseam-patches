// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.media

import app.reseam.patch.Type
import app.reseam.patch.after
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.points
import com.joancomasfdz.baconreader.videosound.bindings.SaveVideosWithSound
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings

private const val SAVE_IMAGE_SERVICE = "com.onelouder.baconreader.imageutils.SaveImageService"

// Reddit videos keep the sound in a separate file, and "Save to device" saves only the picture,
// the video's fallback URL.
val saveVideosWithSound =
    patch("Save videos with sound") {
        description(
            "Adds the sound to Reddit videos saved with \"Save to device\", which were saved without it."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        dependsOn(baconReaderSettings)

        execute {
            // After each download into the file, a first try and then a retry with the full URL,
            // the audio track is added when it succeeded. Not before the method's return, which is
            // a branch target.
            downloads.forEach {
                captureArgumentAs("file", 2)
                    .next { resultOf(Type.Boolean) }
                    .captureAs("downloaded")
                    .after {
                        SaveVideosWithSound.onDownloaded(
                            thisObject,
                            capture("downloaded"),
                            capture("file"),
                            thisObject.field(serviceLink),
                        )
                    }
            }
        }
    }

// Downloads into the chosen folder: getSingleFile(url, folder, name), which calls
// getSingleFile(url, file) twice.
private val downloads =
    klass(SAVE_IMAGE_SERVICE)
        .method("getSingleFile") { returns(Type.Void) }
        .points("downloads") {
            invokeDirect {
                owner(SAVE_IMAGE_SERVICE)
                name("getSingleFile")
                returns(Type.Boolean)
            }
        }

private val serviceLink = klass(SAVE_IMAGE_SERVICE).field("link")
