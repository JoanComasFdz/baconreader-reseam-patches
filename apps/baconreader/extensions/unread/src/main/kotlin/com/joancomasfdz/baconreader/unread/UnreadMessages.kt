// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("UnreadMessages")

package com.joancomasfdz.baconreader.unread

import android.app.NotificationManager
import android.content.Context
import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.common.ErrorDialog
import com.joancomasfdz.baconreader.common.InboxChats
import com.onelouder.baconreader.connectivity.Tasks
import com.onelouder.baconreader.data.MessageManager
import com.onelouder.baconreader.reddit.Comment
import com.onelouder.baconreader.reddit.Listing
import com.onelouder.baconreader.reddit.Message
import com.onelouder.baconreader.reddit.RedditApi
import java.lang.reflect.InvocationTargetException

// The orange inbox envelope of the drawer only followed the messages the inbox or the background
// check loaded, so it stayed orange after the last unread message was marked as read. "Mark read"
// now asks Reddit whether unread messages are left, and updates the envelope.

private const val TAG = "UnreadMessages"

// The app's notification of new messages, from CheckMessagesExecutor.
private const val NOTIFICATION_ID = 1

/**
 * Replaces InboxHelper.setPostStatus, "Mark read": marks the message as read on Reddit, then
 * updates the envelope.
 */
@Export
fun setPostStatus(context: Context?, message: Message?) {
    if (context == null || message == null) return
    try {
        if (message.getNew() != true) return
        message.setNew(false)
        val app = context.applicationContext
        post(app, "api/read_message", mapOf("id" to message.name))
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not mark the message as read", cause(exception))
    }
}

/** A POST through the app's own request code, as its "Mark read" does, then the unread check. */
private fun post(context: Context, path: String, params: Map<String, String?>) {
    // Private: its public callers don't take a listener, so the check couldn't wait for it.
    val redditPost =
        RedditApi::class.java.declaredMethods.firstOrNull {
            it.name == "redditPost" && it.parameterTypes.size == 5
        } ?: throw NoSuchMethodException("RedditApi.redditPost")
    redditPost.isAccessible = true
    redditPost.invoke(
        null,
        context,
        path,
        HashMap(params),
        null,
        object : Tasks.OnCompleteListener<Any?>() {
            override fun onComplete(result: Any?) {
                checkUnread(context)
            }

            override fun onCancel(error: String?) {
                ErrorDialog.report(TAG, "Could not mark the message as read ($path): $error")
                checkUnread(context)
            }
        },
    )
}

/**
 * Asks Reddit for the unread messages with the request of the app's background check,
 * message/unread, and updates the envelope. Chat messages, which the Reddit chat patch counts
 * apart, don't make it orange.
 */
private fun checkUnread(context: Context) {
    try {
        RedditApi.getMessages(
            context,
            "unread",
            null,
            null,
            if (InboxChats.separate) 100 else 1,
            object : Tasks.OnCompleteListener<Listing>() {
                override fun onComplete(result: Listing?) {
                    try {
                        val messages = result?.getChildren(Message::class.java) ?: return
                        val hasUnread = messages.any {
                            !InboxChats.isChat(it.subject, it is Comment)
                        }
                        MessageManager.setNewMessages(hasUnread)
                        if (!hasUnread) {
                            val notifications =
                                context.getSystemService(Context.NOTIFICATION_SERVICE)
                                    as NotificationManager
                            notifications.cancel(NOTIFICATION_ID)
                        }
                    } catch (exception: Exception) {
                        ErrorDialog.report(TAG, "Could not read the unread messages", exception)
                    }
                }

                override fun onCancel(error: String?) {
                    ErrorDialog.report(TAG, "Could not check the unread messages: $error")
                }
            },
        )
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not check the unread messages", exception)
    }
}

/** The error inside a reflection call. */
private fun cause(exception: Exception): Throwable =
    (exception as? InvocationTargetException)?.cause ?: exception
