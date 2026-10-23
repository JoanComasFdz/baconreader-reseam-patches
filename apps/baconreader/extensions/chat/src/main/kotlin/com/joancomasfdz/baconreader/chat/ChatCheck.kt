// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.chat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import com.joancomasfdz.baconreader.common.ErrorDialog
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.joancomasfdz.baconreader.settings.ChatSettings
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONException
import org.json.JSONObject

/**
 * Counts the unread chat messages with Reddit's unofficial chat endpoints, which its website uses:
 * the chat page hands out a token for Reddit's Matrix server, whose /sync tells the unread messages
 * of each chat. It needs the cookies of the login in the chat.
 */
internal object ChatCheck {
    private const val TAG = "ChatCheck"

    private const val CHAT_PAGE = "https://www.reddit.com/chat/"
    private const val MATRIX = "https://matrix.redditspace.com/_matrix/client/v3"

    // Only counts and invites are read: no messages, presence, account data or member lists.
    private const val SYNC_FILTER =
        """{"presence":{"types":[]},"account_data":{"types":[]},"room":{"timeline":{"limit":1},"state":{"types":[],"lazy_load_members":true},"ephemeral":{"types":[]},"account_data":{"types":[]}}}"""

    private const val PREFERENCES = "baconreader_patches"
    private const val KEY_TOKEN = "reddit_chat.token"
    private const val KEY_EXPIRES = "reddit_chat.token_expires"
    private const val KEY_DEVICE = "reddit_chat.device_id"
    private const val KEY_UNREAD = "reddit_chat.unread_count"
    private const val KEY_NOTIFIED = "reddit_chat.notified_count"

    private const val MIN_INTERVAL_MS = 60_000L
    private const val TIMEOUT_MS = 15_000
    private const val MAX_PAGE_BYTES = 4 * 1024 * 1024

    // A token this close to its end is refreshed first.
    private const val EXPIRY_MARGIN_MS = 5 * 60_000L
    private const val DEFAULT_LIFETIME_MS = 60 * 60_000L

    private const val NOTIFICATION_ID = 0x43484154

    // BaconReader's channel for new messages, which NotificationsChannels creates at start.
    private const val APP_CHANNEL = "Messages"
    private const val OWN_CHANNEL = "baconreader_patches_chat"

    // The fallback when the WebView doesn't tell its user agent: a phone's Chrome.
    private const val FALLBACK_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val RS_APP_TOKEN = Regex("""<rs-app[^>]*\stoken="([^"]*)"""")
    private val ENTITY = Regex("""&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);""")
    private val NAMED_ENTITIES =
        mapOf("quot" to "\"", "amp" to "&", "lt" to "<", "gt" to ">", "apos" to "'")

    private val running = AtomicBoolean(false)
    @Volatile private var lastCheck = 0L

    /** While the chat shows, new messages don't notify: the user sees them. */
    @Volatile var chatShowing = false

    var notificationIcon = 0
    var notificationColor = 0

    private class Unauthorized : Exception()

    /** The unread count of the last check, or 0 before the first one and while it's turned off. */
    fun unread(context: Context): Int {
        if (!ChatSettings.checkChatMessages.value) return 0
        return preferences(context).getInt(KEY_UNREAD, 0)
    }

    /** The next check runs even if the last was less than a minute ago. */
    fun checkSoon() {
        lastCheck = 0L
    }

    /**
     * Checks for new chat messages on this thread, at most once a minute, then calls [onChecked] on
     * it. Nothing happens without a login in the chat.
     */
    fun check(context: Context, onChecked: () -> Unit) {
        if (!ChatSettings.checkChatMessages.value) return
        val now = System.currentTimeMillis()
        if (now - lastCheck < MIN_INTERVAL_MS) return
        if (!running.compareAndSet(false, true)) return
        try {
            lastCheck = now
            if (!ChatAccounts.matches(context)) return
            val count = count(context) ?: return
            onCount(context, count)
            onChecked()
        } catch (exception: IOException) {
            Log.i(TAG, "Could not check for chat messages", exception)
        } catch (exception: JSONException) {
            Log.i(TAG, "Reddit's chat answered with something unexpected", exception)
        } catch (exception: Exception) {
            ErrorDialog.report(TAG, "Could not check for chat messages", exception)
        } finally {
            running.set(false)
        }
    }

    /** Called when the chat stops showing: the messages read there change the count. */
    fun onChatHidden() {
        chatShowing = false
        checkSoon()
    }

    /** Called when the chat's login changed to another account's: its token and counts go. */
    fun onAccountSwapped(context: Context) {
        val preferences = preferences(context)
        forgetToken(preferences)
        preferences.edit().remove(KEY_UNREAD).remove(KEY_NOTIFIED).apply()
        checkSoon()
        try {
            notifications(context).cancel(NOTIFICATION_ID)
        } catch (exception: Exception) {
            Log.i(TAG, "Could not clear the chat notification", exception)
        }
    }

    /** Called when the chat shows: its notification is no longer needed. */
    fun onChatShown(context: Context) {
        chatShowing = true
        try {
            notifications(context).cancel(NOTIFICATION_ID)
        } catch (exception: Exception) {
            Log.i(TAG, "Could not clear the chat notification", exception)
        }
    }

    private fun count(context: Context): Int? {
        val cookies = webCookies() ?: return null
        val preferences = preferences(context)
        var token = cachedToken(preferences)
        val cached = token != null
        if (token == null) token = refresh(context, cookies, preferences) ?: return null
        return try {
            sync(token)
        } catch (unauthorized: Unauthorized) {
            forgetToken(preferences)
            if (!cached) return null
            val fresh = refresh(context, cookies, preferences) ?: return null
            try {
                sync(fresh)
            } catch (again: Unauthorized) {
                forgetToken(preferences)
                Log.i(TAG, "Reddit's chat server refused a new token")
                null
            }
        }
    }

    /** The WebView's reddit.com cookies, or null when nobody logged in to the chat. */
    private fun webCookies(): String? {
        val cookies =
            try {
                CookieManager.getInstance().getCookie("https://www.reddit.com")
            } catch (exception: Exception) {
                // Such as while Android updates the WebView.
                Log.i(TAG, "Could not read the chat's cookies", exception)
                null
            } ?: return null
        val pairs = cookies.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        if (pairs.none { it.startsWith("reddit_session=") }) return null
        // Without token_v2, the chat page makes a new token instead of handing out the website's.
        return pairs.filterNot { it.startsWith("token_v2=") }.joinToString("; ")
    }

    private fun cachedToken(preferences: SharedPreferences): String? {
        val token = preferences.getString(KEY_TOKEN, null) ?: return null
        val expires = preferences.getLong(KEY_EXPIRES, 0L)
        return if (System.currentTimeMillis() < expires - EXPIRY_MARGIN_MS) token else null
    }

    private fun forgetToken(preferences: SharedPreferences) {
        preferences.edit().remove(KEY_TOKEN).remove(KEY_EXPIRES).apply()
    }

    /** A new token from the chat page, registered with Reddit's Matrix server. */
    private fun refresh(
        context: Context,
        cookies: String,
        preferences: SharedPreferences,
    ): String? {
        val page = chatPage(context, cookies) ?: return null
        val match = RS_APP_TOKEN.find(page)
        if (match == null) {
            if (page.contains("js_challenge")) {
                // Reddit's bot check, which the chat's WebView passes and keeps a cookie of.
                // It isn't solved here: the next check tries again.
                Log.i(TAG, "Reddit answered the chat page with its bot check")
            } else {
                Log.i(TAG, "The chat page has no chat token; logged out of the chat?")
            }
            return null
        }
        val blob = JSONObject(unescapeHtml(match.groupValues[1]))
        val token = blob.optString("token")
        if (token.isEmpty()) {
            Log.i(TAG, "The chat page's token has no value")
            return null
        }
        val expires =
            blob.optLong("expires", 0L).takeIf { it > 0L }
                ?: jwtExpiry(token)
                ?: (System.currentTimeMillis() + DEFAULT_LIFETIME_MS)
        if (!login(token, preferences)) return null
        preferences.edit().putString(KEY_TOKEN, token).putLong(KEY_EXPIRES, expires).apply()
        return token
    }

    private fun chatPage(context: Context, cookies: String): String? {
        val connection = URL(CHAT_PAGE).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.useCaches = false
            connection.setRequestProperty("Cookie", cookies)
            // The user agent of the WebView the cookies come from.
            connection.setRequestProperty("User-Agent", userAgent(context))
            connection.setRequestProperty(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            )
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                Log.i(TAG, "The chat page answered HTTP $status")
                return null
            }
            return read(connection.inputStream, MAX_PAGE_BYTES)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Registers the token as a session of Reddit's Matrix server, as the website does; then the
     * token itself is the server's access token. The same device every time, so refreshes don't add
     * one.
     */
    private fun login(token: String, preferences: SharedPreferences): Boolean {
        val device = preferences.getString(KEY_DEVICE, null) ?: newDeviceId()
        var answer = postLogin(token, device)
        if (answer.first == HttpURLConnection.HTTP_BAD_REQUEST) {
            // In case the server doesn't take a device of the client's choosing.
            Log.i(TAG, "Chat login with a device id refused: ${answer.second.take(200)}")
            answer = postLogin(token, null)
        }
        val (status, body) = answer
        if (status != HttpURLConnection.HTTP_OK) {
            Log.i(TAG, "Chat login answered HTTP $status: ${body.take(200)}")
            return false
        }
        val deviceId = JSONObject(body).optString("device_id").ifEmpty { device }
        preferences.edit().putString(KEY_DEVICE, deviceId).apply()
        return true
    }

    private fun postLogin(token: String, device: String?): Pair<Int, String> {
        val request =
            JSONObject()
                .put("type", "com.reddit.token")
                .put("token", token)
                .put("initial_device_display_name", "BaconReader")
        if (device != null) request.put("device_id", device)
        val bytes = request.toString().toByteArray(Charsets.UTF_8)
        val connection = URL("$MATRIX/login").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            return status to body(connection, status)
        } finally {
            connection.disconnect()
        }
    }

    /** The unread messages of all chats, plus the chat requests waiting for an answer. */
    private fun sync(token: String): Int? {
        val url =
            "$MATRIX/sync?timeout=0&set_presence=offline&filter=" +
                URLEncoder.encode(SYNC_FILTER, "UTF-8")
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.useCaches = false
            connection.setRequestProperty("Authorization", "Bearer $token")
            val status = connection.responseCode
            val body = body(connection, status)
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED || body.contains("M_UNKNOWN_TOKEN")) {
                throw Unauthorized()
            }
            if (status != HttpURLConnection.HTTP_OK) {
                Log.i(TAG, "Chat sync answered HTTP $status: ${body.take(200)}")
                return null
            }
            return unreadIn(JSONObject(body))
        } finally {
            connection.disconnect()
        }
    }

    private fun unreadIn(sync: JSONObject): Int {
        val rooms = sync.optJSONObject("rooms") ?: return 0
        var count = 0
        val joined = rooms.optJSONObject("join")
        if (joined != null) {
            val ids = joined.keys()
            while (ids.hasNext()) {
                val room = joined.optJSONObject(ids.next()) ?: continue
                val notifications = room.optJSONObject("unread_notifications")
                val notified =
                    maxOf(
                        notifications?.optInt("notification_count", 0) ?: 0,
                        notifications?.optInt("highlight_count", 0) ?: 0,
                    )
                // MSC2654's count of unread messages, which some servers send besides.
                val unread = room.optInt("org.matrix.msc2654.unread_count", 0)
                count += maxOf(notified, unread, 0)
            }
        }
        count += rooms.optJSONObject("invite")?.length() ?: 0
        return count
    }

    /** Notifies when there are more unread messages than last time, and clears it at none. */
    private fun onCount(context: Context, count: Int) {
        val preferences = preferences(context)
        val notified = preferences.getInt(KEY_NOTIFIED, 0)
        if (count == 0) {
            notifications(context).cancel(NOTIFICATION_ID)
        } else if (count > notified && !chatShowing) {
            notify(context, count)
        }
        preferences.edit().putInt(KEY_UNREAD, count).putInt(KEY_NOTIFIED, count).apply()
    }

    private fun notify(context: Context, count: Int) {
        val notifications = notifications(context)
        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, channel(context, notifications))
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context).setDefaults(Notification.DEFAULT_ALL)
            }
        val flags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE
                else 0
        val open = PendingIntent.getActivity(context, NOTIFICATION_ID, intent(context, null), flags)
        builder
            .setSmallIcon(
                if (notificationIcon != 0) notificationIcon else context.applicationInfo.icon
            )
            .setContentTitle(
                if (count == 1) BaconReaderStrings.chatNotificationOne
                else BaconReaderStrings.chatNotificationMany(count.toString())
            )
            .setContentText(BaconReaderStrings.chatNotificationText)
            .setNumber(count)
            .setAutoCancel(true)
            .setContentIntent(open)
        if (notificationColor != 0) {
            try {
                builder.setColor(context.getColor(notificationColor))
            } catch (exception: Exception) {
                Log.i(TAG, "No color for the chat notification", exception)
            }
        }
        notifications.notify(NOTIFICATION_ID, builder.build())
    }

    private fun channel(context: Context, notifications: NotificationManager): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return APP_CHANNEL
        if (notifications.getNotificationChannel(APP_CHANNEL) != null) return APP_CHANNEL
        if (notifications.getNotificationChannel(OWN_CHANNEL) == null) {
            notifications.createNotificationChannel(
                NotificationChannel(
                    OWN_CHANNEL,
                    BaconReaderStrings.chatNotificationChannel,
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }
        return OWN_CHANNEL
    }

    private fun userAgent(context: Context): String =
        try {
            WebSettings.getDefaultUserAgent(context)
        } catch (exception: Exception) {
            Log.i(TAG, "No WebView user agent", exception)
            FALLBACK_USER_AGENT
        }

    private fun newDeviceId(): String {
        val random = SecureRandom()
        return "BR" + String(CharArray(10) { 'A' + random.nextInt(26) })
    }

    /** The `exp` of a JWT, in milliseconds. */
    private fun jwtExpiry(token: String): Long? =
        try {
            val payload = token.split('.').getOrNull(1) ?: return null
            val json =
                String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
            JSONObject(json).optLong("exp", 0L).takeIf { it > 0L }?.times(1000L)
        } catch (exception: Exception) {
            null
        }

    private fun unescapeHtml(text: String): String =
        ENTITY.replace(text) { match ->
            val entity = match.groupValues[1]
            when {
                entity.startsWith("#x") || entity.startsWith("#X") ->
                    entity.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                entity.startsWith("#") ->
                    entity.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                else -> NAMED_ENTITIES[entity]
            } ?: match.value
        }

    private fun body(connection: HttpURLConnection, status: Int): String {
        val stream =
            try {
                if (status >= 400) connection.errorStream else connection.inputStream
            } catch (exception: IOException) {
                null
            } ?: return ""
        return read(stream, MAX_PAGE_BYTES)
    }

    private fun read(stream: InputStream, limit: Int): String = stream.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (out.size() < limit) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        out.toString("UTF-8")
    }

    private fun notifications(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
