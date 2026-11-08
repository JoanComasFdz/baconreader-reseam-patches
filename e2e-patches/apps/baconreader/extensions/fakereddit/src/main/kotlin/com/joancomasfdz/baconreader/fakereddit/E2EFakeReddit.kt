// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("E2EFakeReddit")

package com.joancomasfdz.baconreader.fakereddit

import android.net.Uri
import app.reseam.runtime.Export
import com.onelouder.baconreader.Preferences
import com.onelouder.baconreader.data.DBManager
import com.onelouder.baconreader.data.Session
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject

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

/**
 * Where the fake Reddit hands the app a login at its start, if the test asked it to: Reddit's
 * address, moved to the fake's like every other.
 */
private const val LOGIN_AT_START_URL = "https://www.reddit.com/_e2e/login"

/** How long the app waits at its start for the fake's answer. */
private const val LOGIN_AT_START_TIMEOUT_MS = 10_000

/**
 * Logs the app in as the fake hands it, right after BaconReader.setApplication(), before
 * SessionManager reads the sessions: writes the session and the current user name as a login
 * through the screens does (SessionManager.createSession), so the app starts logged in. The fake
 * answers 204 unless the test asked for it. Asked on a thread of its own: Android throws on a
 * network call on the main thread.
 */
@Export
fun logInAtStart() {
    val login = AtomicReference<JSONObject?>(null)
    val asking = Thread {
        try {
            val connection = URL(LOGIN_AT_START_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = LOGIN_AT_START_TIMEOUT_MS
            connection.readTimeout = LOGIN_AT_START_TIMEOUT_MS
            try {
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    login.set(JSONObject(connection.inputStream.bufferedReader().use { it.readText() }))
                }
            } finally {
                connection.disconnect()
            }
        } catch (exception: Exception) {
            login.set(null)
        }
    }
    asking.start()
    asking.join(LOGIN_AT_START_TIMEOUT_MS.toLong())
    val answer = login.get() ?: return

    val session = Session()
    session.name = answer.getString("name")
    session.accessToken = answer.getString("access_token")
    session.refreshToken = answer.getString("refresh_token")
    // As SessionManager.getExpires(): 15 s early.
    session.expires = System.currentTimeMillis() + (answer.getLong("expires_in") - 15) * 1000
    session.userId = answer.getString("user_id")
    session.createdUtc = answer.getLong("created_utc")
    DBManager.putSession(session)
    Preferences.setUsername(session.name)
}
