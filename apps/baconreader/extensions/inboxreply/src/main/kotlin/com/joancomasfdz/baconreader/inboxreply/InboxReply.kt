// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("InboxReply")

package com.joancomasfdz.baconreader.inboxreply

import android.app.Activity
import android.content.Intent
import android.os.Parcelable
import android.text.SpannableStringBuilder
import android.widget.TextView
import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.common.ErrorDialog
import com.onelouder.baconreader.PostCommentActivity
import com.onelouder.baconreader.data.SessionManager
import com.onelouder.baconreader.reddit.Comment
import com.onelouder.baconreader.reddit.Message
import java.lang.reflect.Modifier

// "Reply" in the inbox opens the app's comment reply screen ("Reply to [user] in r/... as [you]",
// the replied text, formatting and images) instead of the plain "Send message" form. Both post the
// same reply, api/comment to the comment or message. A private message shows on it as a comment
// of the other person.

private const val TAG = "InboxReply"

// PostActivity's text for a comment without a subreddit: a private message.
private const val NO_SUBREDDIT = " in r/null"

// PostActivity.EXTRA_COMMENT.
private const val EXTRA_COMMENT = "EXTRA_COMMENT"

/**
 * Called at the start of InboxHelper.replyMessage. Returns whether the comment reply screen opened;
 * otherwise the app opens its "Send message" form.
 */
@Export
fun openReplyScreen(activity: Activity?, message: Message?): Boolean {
    if (activity == null || message == null) return false
    return try {
        val comment = message as? Comment ?: asComment(message)
        val intent = Intent(activity, PostCommentActivity::class.java)
        // Message and Comment are Parcelable in the app.
        intent.putExtra(EXTRA_COMMENT, comment as Any as Parcelable)
        activity.startActivity(intent)
        true
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not open the comment reply screen", exception)
        false
    }
}

/**
 * A private message as a Comment, its subclass, which the reply screen takes: the same fields, and
 * as author the other person, since a sent message's author is the user.
 */
private fun asComment(message: Message): Comment {
    val comment = Comment()
    var type: Class<*>? = Message::class.java
    while (type != null) {
        for (field in type.declaredFields) {
            if (Modifier.isStatic(field.modifiers)) continue
            field.isAccessible = true
            field.set(comment, field.get(message))
        }
        type = type.superclass
    }
    val me = SessionManager.getUsername()
    val dest = comment.dest
    if (dest != null && me != null && me == comment.author) comment.author = dest
    return comment
}

/**
 * Called after the reply screen sets "Reply to [user] in r/[subreddit] as [you]": a private message
 * has no subreddit, which shows as "in r/null".
 */
@Export
fun fixReplyText(textView: TextView?) {
    val text = textView?.text ?: return
    val index = text.indexOf(NO_SUBREDDIT)
    if (index < 0) return
    textView.text = SpannableStringBuilder(text).delete(index, index + NO_SUBREDDIT.length)
}
