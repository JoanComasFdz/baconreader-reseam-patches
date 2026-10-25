// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.comments

import app.reseam.patch.MethodTarget
import app.reseam.patch.after
import app.reseam.patch.dex.AccessFlags
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.point
import app.reseam.patch.settings.section
import app.reseam.patch.types.NewField
import com.joancomasfdz.baconreader.avatars.bindings.UserAvatars
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.joancomasfdz.baconreader.settings.UserAvatarsSettings
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.SettingsPages
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings

/** The superclass of Comment, also used for the inbox's private messages. */
private const val MESSAGE = "com.onelouder.baconreader.reddit.Message"

// BaconReader shows no avatars. Reddit sends the author's id with every comment and message, and
// its API gives the avatar of up to 100 ids at once.
val userAvatars =
    patch("Show user avatars") {
        description(
            "Adds an option to show the author's avatar before the name in comments, as on Reddit's website."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        settings(
            baconReaderSettings,
            section(
                SettingsPages.Comments,
                BaconReaderStrings.sectionUserAvatars,
                UserAvatarsSettings.userAvatars,
                UserAvatarsSettings.userAvatarSize,
                UserAvatarsSettings.userAvatarBackground,
            ),
        )

        execute {
            // The app's Message class has no field for the author's id. The app's JSON parser,
            // Jackson, fills public fields by name.
            message.classDef.addField(
                NewField(
                    name = "author_fullname",
                    fieldType = "Ljava/lang/String;",
                    accessFlags = AccessFlags.PUBLIC.toUInt(),
                    initialValue = null,
                )
            )

            commentSubText.after {
                returnValue(UserAvatars.withAvatar(param(0), param(1), result))
            }
            commentSubTextSet.after { UserAvatars.bindAvatars(capture("view")) }
            replyTextSet.after { UserAvatars.replyAvatar(capture("view")) }
            inboxLabelSet.after {
                UserAvatars.inboxAvatar(thisObject.field(inboxMessage), capture("view"))
            }
        }
    }

private val message = klass(MESSAGE)

// Builds the author line of a comment: "author [flair] points time".
private val commentSubText =
    klass("com.onelouder.baconreader.parser.SubTextBuilder").method("getCommentSubText")

// The view to redraw when the avatar arrives: this.commentSubText.setText(subText).
private val commentSubTextSet =
    klass("com.onelouder.baconreader.adapters.CommentHolder")
        .method("updateView")
        .setTextOf("commentSubText")

// Shows "Reply to [author] in r/... as [you]" and the replied comment or post on the reply screen.
private val replyTextSet =
    klass("com.onelouder.baconreader.PostActivity")
        .method("updateReplyText")
        .setTextOf("subTextView")

// Shows an inbox item: comment and post replies, mentions and private messages.
private val inboxLabelSet =
    klass("com.onelouder.baconreader.adapters.InboxHolder")
        .method("updateView")
        .setTextOf("labelView")

private val inboxMessage = klass("com.onelouder.baconreader.adapters.InboxHolder").field("message")

/** The setText(CharSequence) on the TextView read from [fieldName], with the view captured. */
private fun MethodTarget.setTextOf(fieldName: String) =
    point("$fieldName.setText") {
            field { name(fieldName) }
            then {
                invokeVirtual {
                    name("setText")
                    params("java.lang.CharSequence")
                }
            }
        }
        .captureArgumentAs("view", 0)
