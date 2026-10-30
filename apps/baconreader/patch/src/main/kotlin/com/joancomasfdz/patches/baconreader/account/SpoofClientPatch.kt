// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.account

import app.reseam.patch.alwaysReturn
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM

private const val ORIGINAL_CLIENT_ID = "zACVn0dSFGdWqQ"
private const val ORIGINAL_CLIENT_SECRET = "kDm2tYpu9DqyWFFyPlNcXGEni4k"
private const val ORIGINAL_REDIRECT_URI = "http://baconreader.com/auth"

private const val REDREADER_CLIENT_ID = "yH0aTnJEt6qUgGn835B4vg"
private const val REDREADER_REDIRECT_URI = "redreader://rr_oauth_redir"
private const val REDREADER_USER_AGENT = "org.quantumbadger.redreader/1.25.1"

// Reddit stopped accepting BaconReader's OAuth client when BaconReader shut down in 2023, so
// logging in and loading anything fails. Another app's client still works, as long as the redirect
// URI and user agent match what Reddit has on record for it. RedReader's is the default.
val spoofClient =
    patch("Spoof client") {
        description(
            "Makes logging in to Reddit work again by using another Reddit app's client ID."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)

        val clientId =
            stringOption(
                "clientId",
                title = "Application client ID",
                description =
                    "The client ID of an \"Installed app\" from https://www.reddit.com/prefs/apps.",
                default = REDREADER_CLIENT_ID,
                required = true,
            )
        val redirectUri =
            stringOption(
                "redirectUri",
                title = "Application redirect URI",
                description =
                    "The redirect URI of that Reddit app. Empty keeps BaconReader's own, $ORIGINAL_REDIRECT_URI.",
                default = REDREADER_REDIRECT_URI,
            )
        val userAgent =
            stringOption(
                "userAgent",
                title = "Application user agent",
                description =
                    "The user agent of that Reddit app, ideally. Empty keeps BaconReader's own.",
                default = REDREADER_USER_AGENT,
            )

        execute {
            val id = options[clientId]
            val uri = options.getOrNull(redirectUri)?.takeIf { it.isNotBlank() }
            val agent = options.getOrNull(userAgent)?.takeIf { it.isNotBlank() }

            // Reddit's redirects from ssl.reddit.com to www.reddit.com break logging in.
            val urls =
                bytecode.replaceStringsContaining("ssl.reddit.com") {
                    it.replace("ssl.reddit.com", "www.reddit.com")
                }
            log.info("Moved $urls URLs from ssl.reddit.com to www.reddit.com")

            authorizeUrl.method.replaceAllStrings("client_id=$ORIGINAL_CLIENT_ID", "client_id=$id")
            tokenRequest.method.replaceAllStrings(ORIGINAL_CLIENT_ID, id)

            if (uri != null) {
                authorizeUrl.method.replaceAllStrings(
                    "redirect_uri=$ORIGINAL_REDIRECT_URI",
                    "redirect_uri=$uri",
                )
                redirectCheck.method.replaceAllStrings(ORIGINAL_REDIRECT_URI, uri)
                codeExchange.method.replaceAllStrings(ORIGINAL_REDIRECT_URI, uri)
            }

            if (agent != null) {
                restClientUserAgent.alwaysReturn(agent)
                retrofitUserAgent.alwaysReturn(agent)
            }
        }
    }

private val authorizeUrl =
    method("authorizeUrl") {
        strings("client_id=$ORIGINAL_CLIENT_ID", "redirect_uri=$ORIGINAL_REDIRECT_URI")
    }

// Sends the client ID and secret as Basic auth to get and refresh tokens.
private val tokenRequest =
    method("tokenRequest") { strings(ORIGINAL_CLIENT_ID, ORIGINAL_CLIENT_SECRET) }

// Tells the login web view when Reddit redirects back to the app.
private val redirectCheck =
    method("redirectCheck") {
        name("isRedirectUrl")
        strings(ORIGINAL_REDIRECT_URI)
    }

// Reddit only trades the authorization code for a token when the redirect URI matches.
private val codeExchange =
    method("codeExchange") {
        name("runTask")
        strings(ORIGINAL_REDIRECT_URI)
    }

private val restClientUserAgent =
    klass("com.onelouder.baconreader.connectivity.RestClient").method("getUserAgent")

private val retrofitUserAgent =
    klass("com.onelouder.baconreader.reddit.RedditRetrofitClientModule").method("getUserAgent")
