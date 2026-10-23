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
