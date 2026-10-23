// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("EmbedCommentImages")

package com.joancomasfdz.baconreader.embedcommentimages

import android.widget.TextView
import app.reseam.runtime.Export

/**
 * Called right after BaconReader sets the text of a comment. Reddit's API now sends the same
 * placeholder image for every emote, so the real ones are found from the comment's [markdown].
 */
@Export
fun embedComment(textView: TextView?, markdown: String?) {
    if (textView != null) CommentImages.embed(textView, markdown)
}

/** Called right after BaconReader sets the text of a post. */
@Export
fun embed(textView: TextView?) {
    if (textView != null) CommentImages.embed(textView, null)
}
