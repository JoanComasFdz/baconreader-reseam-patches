// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.inbox

import app.reseam.patch.dex.Opcode
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.point
import app.reseam.patch.replace
import app.reseam.patch.skipWhen
import com.joancomasfdz.baconreader.unread.bindings.UnreadMessages
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings

private const val INBOX_HELPER = "com.onelouder.baconreader.adapters.InboxHelper"

// "Mark read" only told Reddit, and never turned the orange inbox envelope back to normal. And
// Reddit answers api/unread_message with 404, so "Mark unread" did nothing.
val fixUnreadMessages =
    patch("Fix unread messages") {
        description(
            "Turns the orange inbox envelope back to normal when the last unread message is marked as read, and removes \"Mark unread\", which Reddit no longer supports."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        dependsOn(baconReaderSettings)

        execute {
            setPostStatus.replace {
                UnreadMessages.setPostStatus(thisObject.field(inboxActivity), param(0))
                returnVoid()
            }
            markUnreadItem.skipWhen { bool(true) }
        }
    }

private val inboxHelper = klass(INBOX_HELPER)

private val inboxActivity = inboxHelper.field("activity")

// "Mark read" and "Mark unread" of an inbox item.
private val setPostStatus = inboxHelper.method("setPostStatus")

// Fills the menu of an inbox item: menu.add(new InboxMenuItem(4, R.string.menu_mark_unread_title)),
// whose result isn't used. 4 is the item's type, which the menu's click handler switches on.
private val markUnreadItem =
    inboxHelper.method("onOpen").point("markUnreadItem") {
        literal(4)
        then { opcode(Opcode.CONST) }
        then { invokeDirect { owner("$INBOX_HELPER\$InboxMenuItem") } }
        then { invokeVirtual { name("add") } }
    }
