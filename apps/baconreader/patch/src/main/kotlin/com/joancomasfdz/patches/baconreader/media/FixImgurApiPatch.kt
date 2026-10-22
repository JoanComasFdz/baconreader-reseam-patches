// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.media

import app.reseam.patch.after
import app.reseam.patch.alwaysReturn
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.point
import com.joancomasfdz.baconreader.imgur.bindings.ImgurFix
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM

private const val IMGUR_API_URL = "https://api.imgur.com/3/"

// BaconReader calls Imgur's API through a RapidAPI proxy whose subscription ended, so it answers
// 403 to every request: Imgur albums and galleries don't load. Imgur's own API accepts the same
// requests and client ID, but rejects the image uploads of comments and posts, which BaconReader
// sends without a content type.
val fixImgurApi =
    patch("Fix Imgur API") {
        description(
            "Makes Imgur albums and galleries load again, and image uploads in comments and posts work."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)

        execute {
            imgurBaseUrl.alwaysReturn(IMGUR_API_URL)
            uploadAuthorization.after { ImgurFix.setUploadContentType(capture("connection")) }
        }
    }

private val imgurBaseUrl =
    klass("com.onelouder.baconreader.imageutils.ImgurV3Constants").method("getBaseURL")

// The upload adds the authorization to the connection, then sends the file.
private val uploadAuthorization =
    klass("com.onelouder.baconreader.imageutils.ImgurUpload")
        .method("run")
        .point("uploadAuthorization") {
            invokeVirtual {
                owner("com.onelouder.baconreader.imageutils.ImgurAuthorization")
                name("addToHttpURLConnection")
            }
        }
        .captureArgumentAs("connection", 1)
