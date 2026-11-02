// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("E2EFakeReddit")

package com.joancomasfdz.baconreader.fakereddit

import android.net.Uri
import app.reseam.runtime.Export

// Test only. The fake Reddit serves Reddit's media under <baseUrl>/_media/<host>/, over plain HTTP,
// where "E2E fake Reddit" moves the app's own media URLs. Two of BaconReader's checks assume
// Reddit's own addresses: a Reddit video's host is v.redd.it, and a saved video's URL is https.

/** The path where the fake Reddit serves what v.redd.it serves. */
private const val MOVED_VIDEO_PATH = "/_media/v.redd.it/"

/** The path where the fake Reddit serves media. */
private const val MEDIA_PATH = "/_media/"

/**
 * Whether the URL is a v.redd.it video moved to the fake Reddit, at the start of
 * RedditVideo.isValid(url): true makes it a Reddit video, false lets the app check it.
 */
@Export
fun isMovedRedditVideo(url: String?): Boolean =
    try {
        url != null && Uri.parse(url).path?.startsWith(MOVED_VIDEO_PATH) == true
    } catch (exception: Exception) {
        false
    }

/**
 * The URL SaveImage.getVideoFilename(url) returns, which it turns to https: for media of the fake
 * Reddit, the URL with its own scheme back, since the fake answers plain HTTP.
 */
@Export
fun keepFakeScheme(url: String?, result: String?): String? =
    try {
        if (
            url != null &&
                result != null &&
                url.startsWith("http:") &&
                result.startsWith("https:") &&
                Uri.parse(url).path?.startsWith(MEDIA_PATH) == true
        ) {
            "http:" + result.removePrefix("https:")
        } else {
            result
        }
    } catch (exception: Exception) {
        result
    }
