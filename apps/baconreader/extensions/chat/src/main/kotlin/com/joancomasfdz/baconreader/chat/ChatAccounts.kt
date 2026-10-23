// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.chat

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import com.onelouder.baconreader.data.SessionManager
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/**
 * Keeps a chat login for each BaconReader account. The chat's web view has one set of reddit.com
 * cookies, the website's login: when another account shows, the cookies of the last one are put
 * aside and those of the new one, if it logged in to the chat before, are put back.
 *
 * A new login in the chat belongs to the account shown when it happens. A login from before this
 * kept them per account is asked for its user name once.
 */
internal object ChatAccounts {
    private const val TAG = "ChatAccounts"

    private const val SITE = "https://www.reddit.com"
    private const val ME = "https://www.reddit.com/api/me.json"
    private const val SESSION_COOKIE = "reddit_session"

    private const val PREFERENCES = "baconreader_patches"
    private const val KEY_ACCOUNT = "reddit_chat.login_account"
    private const val KEY_SESSION = "reddit_chat.login_session"
    private const val KEY_SAVED = "reddit_chat.login_cookies."

    // Reddit renews its cookies while the chat is used.
    private const val RESTORED_MAX_AGE = 180L * 24 * 60 * 60
    private const val TIMEOUT_MS = 15_000
    private const val MIN_IDENTIFY_INTERVAL_MS = 60_000L

    private val main = Handler(Looper.getMainLooper())
    private val identifying = AtomicBoolean(false)
    @Volatile private var lastIdentify = 0L

    /**
     * On the main thread: when the account shown isn't the one the chat is logged in with, swaps
     * their logins and calls [onSwapped].
     */
    fun follow(context: Context, onSwapped: () -> Unit) {
        val preferences = preferences(context)
        val account = current()
        val cookies = cookies()
        val session = cookies?.let { sessionOf(it) }
        val owner = preferences.getString(KEY_ACCOUNT, null)
        if (session != null) {
            val hash = hash(session)
            if (owner == null) {
                identify(context, cookies, hash, onSwapped)
                return
            }
            if (hash != preferences.getString(KEY_SESSION, null)) {
                preferences
                    .edit()
                    .putString(KEY_ACCOUNT, account)
                    .putString(KEY_SESSION, hash)
                    .apply()
                return
            }
        } else if (owner == null) {
            preferences.edit().putString(KEY_ACCOUNT, account).remove(KEY_SESSION).apply()
            return
        }
        if (owner.equals(account, ignoreCase = true)) return

        val saved = preferences.getString(savedKey(account), null)
        val editor = preferences.edit()
        if (session != null) editor.putString(savedKey(owner), cookies)
        else editor.remove(savedKey(owner))
        editor
            .remove(savedKey(account))
            .putString(KEY_ACCOUNT, account)
            .putString(KEY_SESSION, saved?.let { sessionOf(it) }?.let { hash(it) })
            .apply()
        val manager = CookieManager.getInstance()
        if (cookies != null) clear(manager, cookies)
        if (saved != null) restore(manager, saved)
        manager.flush()
        // The chat's own data, such as its Matrix session, belongs to the last login too.
        WebStorage.getInstance().deleteOrigin(SITE)
        Log.i(TAG, "Chat login switched to the account shown")
        onSwapped()
    }

    /** True when the chat's login may be counted for the account shown. */
    fun matches(context: Context): Boolean {
        val session = cookies()?.let { sessionOf(it) } ?: return true
        val preferences = preferences(context)
        // Not known yet: counted as before logins were kept per account.
        val owner = preferences.getString(KEY_ACCOUNT, null) ?: return true
        // A new login, which belongs to the account shown.
        if (hash(session) != preferences.getString(KEY_SESSION, null)) return true
        return owner.equals(current(), ignoreCase = true)
    }

    /** The BaconReader account shown, or "" while none is logged in. */
    private fun current(): String = SessionManager.getUsername() ?: ""

    /**
     * Asks Reddit, in the background, whose login the chat has, then follows the account shown with
     * it. Until it answers, nothing is swapped; a failure asks again on the next call, at most once
     * a minute.
     */
    private fun identify(context: Context, cookies: String, hash: String, onSwapped: () -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastIdentify < MIN_IDENTIFY_INTERVAL_MS) return
        if (!identifying.compareAndSet(false, true)) return
        lastIdentify = now
        val userAgent =
            try {
                WebSettings.getDefaultUserAgent(context)
            } catch (exception: Exception) {
                null
            }
        Thread(
                {
                    try {
                        val name = userName(cookies, userAgent) ?: return@Thread
                        val account =
                            SessionManager.getNames().firstOrNull {
                                it.equals(name, ignoreCase = true)
                            } ?: current()
                        main.post {
                            val preferences = preferences(context)
                            if (preferences.getString(KEY_ACCOUNT, null) != null) return@post
                            preferences
                                .edit()
                                .putString(KEY_ACCOUNT, account)
                                .putString(KEY_SESSION, hash)
                                .apply()
                            follow(context, onSwapped)
                        }
                    } catch (exception: Exception) {
                        Log.i(TAG, "Could not tell whose login the chat has", exception)
                    } finally {
                        identifying.set(false)
                    }
                },
                "RedditChatAccount",
            )
            .start()
    }

    /** The user name of the website's login, or null when Reddit doesn't tell it. */
    private fun userName(cookies: String, userAgent: String?): String? {
        val connection = URL(ME).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.useCaches = false
            connection.setRequestProperty("Cookie", cookies)
            if (userAgent != null) connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.i(TAG, "Reddit answered HTTP ${connection.responseCode} for the chat's user")
                return null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(body).optJSONObject("data")?.optString("name")?.ifEmpty { null }
        } finally {
            connection.disconnect()
        }
    }

    private fun cookies(): String? =
        try {
            CookieManager.getInstance().getCookie(SITE)?.ifBlank { null }
        } catch (exception: Exception) {
            // Such as while Android updates the WebView.
            Log.i(TAG, "Could not read the chat's cookies", exception)
            null
        }

    private fun pairs(cookies: String): List<String> =
        cookies.split(';').map { it.trim() }.filter { it.contains('=') }

    private fun sessionOf(cookies: String): String? =
        pairs(cookies)
            .firstOrNull { it.startsWith("$SESSION_COOKIE=") }
            ?.substringAfter('=')
            ?.ifEmpty { null }

    /** Expires the cookies, set on reddit.com or only on www.reddit.com. */
    private fun clear(manager: CookieManager, cookies: String) {
        for (pair in pairs(cookies)) {
            val name = pair.substringBefore('=')
            manager.setCookie(SITE, "$name=; Max-Age=0; Path=/")
            manager.setCookie(SITE, "$name=; Max-Age=0; Domain=.reddit.com; Path=/")
        }
    }

    /**
     * Puts the cookies back on reddit.com. Android doesn't tell their own attributes, so they're
     * secure, and the login cookie is also hidden from the page's scripts, as Reddit sets it.
     */
    private fun restore(manager: CookieManager, cookies: String) {
        for (pair in pairs(cookies)) {
            val httpOnly = if (pair.startsWith("$SESSION_COOKIE=")) "; HttpOnly" else ""
            manager.setCookie(
                SITE,
                "$pair; Domain=.reddit.com; Path=/; Secure; Max-Age=$RESTORED_MAX_AGE$httpOnly",
            )
        }
    }

    private fun savedKey(account: String) = KEY_SAVED + account.lowercase(Locale.ROOT)

    private fun hash(value: String): String =
        Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP,
        )

    private fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
