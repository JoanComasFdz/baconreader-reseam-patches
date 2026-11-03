// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("ImgurFix")

package com.joancomasfdz.baconreader.imgur

import app.reseam.runtime.Export
import java.net.HttpURLConnection

/**
 * BaconReader uploads the raw file without a content type, so Android sends it as
 * "application/x-www-form-urlencoded", which Imgur's API answers with 400 Bad Request.
 */
@Export
fun setUploadContentType(connection: HttpURLConnection?) {
    connection?.setRequestProperty("Content-Type", "application/octet-stream")
}

/**
 * Whether an image of an Imgur album is a video. Imgur's API sends "mp4" empty for a still image,
 * which BaconReader takes for a video because it only checks it for null.
 */
@Export fun isVideo(mp4: String?): Boolean = !mp4.isNullOrEmpty()

/**
 * What the album viewer plays or saves: the image's video, or the image itself when it has none.
 */
@Export fun link(mp4: String?, link: String?): String? = if (mp4.isNullOrEmpty()) link else mp4
