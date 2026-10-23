// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.inbox

import app.reseam.patch.after
import app.reseam.patch.before
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.point
import com.joancomasfdz.baconreader.inboxreply.bindings.InboxReply
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings

private const val INBOX_HELPER = "com.onelouder.baconreader.adapters.InboxHelper"
private const val POST_ACTIVITY = "com.onelouder.baconreader.PostActivity"

// Replying from the inbox opens a plain "Send message" form, without the comment or message being
// replied to. The comment reply screen shows it, with formatting, and posts the same reply.
val inboxCommentReply =
    patch("Inbox comment reply screen") {
        description(
            "Opens the comment reply screen, with the replied comment or message, when replying from the inbox, instead of the plain \"Send message\" form."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        dependsOn(baconReaderSettings)

        execute {
            // Comment replies, mentions and private messages.
            replyMessage.before {
                whenTrue(InboxReply.openReplyScreen(thisObject.field(inboxActivity), param(0))) {
                    returnVoid()
                }
            }

            // A private message has no subreddit: "Reply to [user] in r/null as [you]".
            replyTextSet.after { InboxReply.fixReplyText(capture("view")) }
        }
    }

private val inboxActivity = klass(INBOX_HELPER).field("activity")

// "Reply" of an inbox item: opens the "Send message" form.
private val replyMessage = klass(INBOX_HELPER).method("replyMessage")

// this.subTextView.setText("Reply to [user] in r/... as [you]") on the reply screen.
private val replyTextSet =
    klass(POST_ACTIVITY)
        .method("updateReplyText")
        .point("replyTextSet") {
            field {
                owner(POST_ACTIVITY)
                name("subTextView")
            }
        }
        .next { invokeVirtual { name("setText") } }
        .captureArgumentAs("view", 0)
