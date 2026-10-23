// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.common

import com.joancomasfdz.baconreader.settings.ChatSettings

/**
 * Reddit also sends each chat message to the inbox, as a private message titled "[direct chat
 * room]". The Reddit chat patch takes them out of the inbox and counts them next to Chat; the other
 * patches that count unread messages leave them out too.
 */
object InboxChats {
    private val SUBJECT = Regex("""\[[a-z ]*chat room]""", RegexOption.IGNORE_CASE)

    /** Set when the Reddit chat patch is in the app. */
    @Volatile var patched = false

    /** True while chat messages are kept apart from the inbox. */
    val separate: Boolean
        get() = patched && ChatSettings.checkChatMessages.value

    /** True for a chat message, while they're kept apart from the inbox. */
    fun isChat(subject: String?, isComment: Boolean): Boolean =
        separate && !isComment && subject != null && SUBJECT.matches(subject.trim())
}
