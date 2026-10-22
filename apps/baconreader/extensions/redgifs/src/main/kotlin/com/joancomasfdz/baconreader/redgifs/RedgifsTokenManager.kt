// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.redgifs

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

// Reference: https://github.com/JeffreyCA/Apollo-ImprovedCustomApi/pull/67
internal object RedgifsTokenManager {
    // Expire after 23 hours to leave some breathing room.
    private const val EXPIRY_SECONDS = 23L * 60 * 60

    private const val TEMPORARY_TOKEN_URL = "https://api.redgifs.com/v2/auth/temporary"

    class RedgifsToken(val accessToken: String, private val issuedAtSeconds: Long) {
        val expiresAtSeconds: Long
            get() = issuedAtSeconds + EXPIRY_SECONDS

        val isValid: Boolean
            get() = expiresAtSeconds >= nowInSeconds()
    }

    // Redgifs ties a token to the user agent it was requested with.
    private val tokens = HashMap<String, RedgifsToken>()

    fun refreshToken(userAgent: String, force: Boolean): RedgifsToken =
        synchronized(tokens) {
            tokens[userAgent]?.takeIf { !force && it.isValid }
                ?: RedgifsToken(requestToken(userAgent), nowInSeconds()).also {
                    tokens[userAgent] = it
                }
        }

    fun oAuthResponseBody(token: RedgifsToken): String =
        JSONObject()
            .put("access_token", token.accessToken)
            .put("expiry_time", token.expiresAtSeconds - nowInSeconds())
            .put("scope", "read")
            .put("token_type", "Bearer")
            .toString()

    private fun requestToken(userAgent: String): String {
        val connection = URL(TEMPORARY_TOKEN_URL).openConnection() as HttpURLConnection
        try {
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(body).getString("token")
        } finally {
            connection.disconnect()
        }
    }

    private fun nowInSeconds() = System.currentTimeMillis() / 1000
}
