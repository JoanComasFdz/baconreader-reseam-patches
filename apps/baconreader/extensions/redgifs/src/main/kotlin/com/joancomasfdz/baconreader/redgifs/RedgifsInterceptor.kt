// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.redgifs

import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONException

internal object RedgifsInterceptor : Interceptor {
    private const val TAG = "RedgifsFix"

    // BaconReader sends this static user agent on Redgifs API calls.
    private const val DEFAULT_USER_AGENT = "BaconReader"

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != "api.redgifs.com") return chain.proceed(request)

        var userAgent = DEFAULT_USER_AGENT
        var forceTokenRefresh = false
        if (request.header("Authorization") != null) {
            val response =
                chain.proceed(request.newBuilder().header("User-Agent", userAgent).build())
            if (response.isSuccessful) return response
            // An interceptor later in the chain may have overwritten the user agent, and the token
            // has to be requested with the one the request goes out with.
            userAgent = response.request.header("User-Agent") ?: userAgent
            response.close()
            // A token can stop working before it expires, after an IP change or a Redgifs error.
            forceTokenRefresh = true
        }

        try {
            val token = RedgifsTokenManager.refreshToken(userAgent, forceTokenRefresh)
            // BaconReader still asks the removed OAuth endpoint for its token, so it gets the
            // temporary one in the shape that endpoint used.
            if (request.url.encodedPath == "/v2/oauth/client") {
                return jsonResponse(request, RedgifsTokenManager.oAuthResponseBody(token))
            }
            return chain.proceed(
                request
                    .newBuilder()
                    .header("Authorization", "Bearer " + token.accessToken)
                    .header("User-Agent", userAgent)
                    .build()
            )
        } catch (exception: JSONException) {
            Log.e(TAG, "Could not parse the Redgifs token response", exception)
            throw IOException(exception)
        }
    }

    private fun jsonResponse(request: Request, body: String): Response =
        Response.Builder()
            .message("OK")
            .code(HttpURLConnection.HTTP_OK)
            .protocol(Protocol.HTTP_1_1)
            .request(request)
            .header("Content-Type", "application/json")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
}
