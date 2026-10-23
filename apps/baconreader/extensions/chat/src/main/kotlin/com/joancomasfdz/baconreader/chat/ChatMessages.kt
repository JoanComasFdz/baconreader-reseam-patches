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
import android.util.Log
import com.joancomasfdz.baconreader.common.InboxChats
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.onelouder.baconreader.Preferences
import com.onelouder.baconreader.data.SessionManager
import com.onelouder.baconreader.reddit.Comment
import com.onelouder.baconreader.reddit.Message
import com.onelouder.baconreader.reddit.RedditApi

/**
 * The chat messages Reddit sends to the inbox. BaconReader's own message check, which runs when the
 * app comes to the foreground and at its update interval, hands them over: they're counted for
 * Chat, notified as chat messages, and marked as read on Reddit when the chat shows.
 */
internal object ChatMessages {
    private const val TAG = "ChatMessages"

    private const val PREFERENCES = "baconreader_patches"
    private const val KEY_ACCOUNT = "reddit_chat.unread_account"
    private const val KEY_UNREAD = "reddit_chat.unread_ids"
    private const val KEY_NOTIFIED = "reddit_chat.notified_time"

    // Left by earlier versions, which asked Reddit's chat server for the unread messages.
    private val OLD_KEYS =
        listOf(
            "reddit_chat.token",
            "reddit_chat.token_expires",
            "reddit_chat.device_id",
            "reddit_chat.unread_count",
            "reddit_chat.notified_count",
        )

    private const val NOTIFICATION_ID = 0x43484154

    // BaconReader's channel for new messages, which NotificationsChannels creates at start.
    private const val APP_CHANNEL = "Messages"
    private const val OWN_CHANNEL = "baconreader_patches_chat"

    /**
     * While the chat shows, new messages don't notify, and are marked as read: the user sees them.
     */
    @Volatile var chatShowing = false

    var notificationIcon = 0
    var notificationColor = 0

    fun init(context: Context) {
        val editor = preferences(context).edit()
        for (key in OLD_KEYS) editor.remove(key)
        editor.apply()
    }

    fun isChat(message: Any?): Boolean =
        message is Message && InboxChats.isChat(message.subject, message is Comment)

    /** The unread chat messages of the last message check, for the account shown. */
    fun unread(context: Context): Int {
        if (!InboxChats.separate) return 0
        return unreadIds(preferences(context))?.size ?: 0
    }

    /**
     * The unread messages of BaconReader's message check: keeps the chat messages' ids, notifies of
     * new ones, and returns the other messages, which the app notifies of.
     */
    fun onUnread(context: Context, messages: List<*>): List<*> {
        val chats = messages.filter { isChat(it) }.map { it as Message }
        if (chats.isEmpty() && !InboxChats.separate) return messages
        val preferences = preferences(context)
        val ids = chats.mapNotNull { it.name }
        if (chatShowing) {
            markRead(context, ids)
            preferences.edit().remove(KEY_UNREAD).apply()
        } else {
            preferences
                .edit()
                .putString(KEY_ACCOUNT, account())
                .putString(KEY_UNREAD, ids.joinToString(","))
                .apply()
        }
        val newest = chats.maxOfOrNull { it.created_utc } ?: 0L
        val notified = preferences.getLong(KEY_NOTIFIED, 0L)
        if (chats.isEmpty() || chatShowing) {
            cancelNotification(context)
        } else if (newest > notified && Preferences.getNotifyOnMessage()) {
            notify(context, chats)
        }
        if (newest > notified) preferences.edit().putLong(KEY_NOTIFIED, newest).apply()
        return messages.filterNot { isChat(it) }
    }

    /** Called when the chat shows: its messages are seen. */
    fun onChatShown(context: Context) {
        chatShowing = true
        cancelNotification(context)
        val preferences = preferences(context)
        val ids = unreadIds(preferences) ?: return
        preferences.edit().remove(KEY_UNREAD).apply()
        markRead(context, ids)
    }

    fun onChatHidden() {
        chatShowing = false
    }

    private fun unreadIds(preferences: SharedPreferences): List<String>? {
        if (preferences.getString(KEY_ACCOUNT, null) != account()) return null
        return preferences
            .getString(KEY_UNREAD, null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?.ifEmpty { null }
    }

    /** Marks the messages as read on Reddit, through the app's own request code. */
    private fun markRead(context: Context, ids: List<String>) {
        if (ids.isEmpty()) return
        // Reddit takes up to 100 ids, separated by commas.
        for (batch in ids.chunked(100)) RedditApi.readMessage(context, batch.joinToString(","))
    }

    private fun account(): String = SessionManager.getUsername() ?: ""

    private fun cancelNotification(context: Context) {
        try {
            notifications(context).cancel(NOTIFICATION_ID)
        } catch (exception: Exception) {
            Log.i(TAG, "Could not clear the chat notification", exception)
        }
    }

    private fun notify(context: Context, chats: List<Message>) {
        val notifications = notifications(context)
        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, channel(notifications))
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context).setDefaults(Notification.DEFAULT_ALL)
            }
        val flags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE
                else 0
        val open = PendingIntent.getActivity(context, NOTIFICATION_ID, intent(context, null), flags)
        val count = chats.size
        val newest = chats.maxBy { it.created_utc }
        val text =
            if (newest.author.isNullOrEmpty()) BaconReaderStrings.chatNotificationText
            else "${newest.author}: ${newest.body.orEmpty().trim()}"
        builder
            .setSmallIcon(
                if (notificationIcon != 0) notificationIcon else context.applicationInfo.icon
            )
            .setContentTitle(
                if (count == 1) BaconReaderStrings.chatNotificationOne
                else BaconReaderStrings.chatNotificationMany(count.toString())
            )
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
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

    private fun channel(notifications: NotificationManager): String {
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

    private fun notifications(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
