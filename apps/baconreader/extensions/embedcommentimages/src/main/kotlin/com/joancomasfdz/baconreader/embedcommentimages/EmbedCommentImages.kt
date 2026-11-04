// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("EmbedCommentImages")

package com.joancomasfdz.baconreader.embedcommentimages

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import app.reseam.runtime.Export

/**
 * Called right after BaconReader sets the text of a comment. Reddit's API now sends the same
 * placeholder image for every emote, so the real ones are found from the comment's [markdown]. The
 * media of its bare links show where the links are.
 */
@Export
fun embedComment(textView: TextView?, markdown: String?) {
    if (textView != null) CommentImages.embedInPlace(textView, markdown)
}

/**
 * Called right after CommentHolder gives the comment body its touch listener, which the copies of
 * the body that show the text after a media link get too.
 */
@Export
fun rememberTouch(view: View?, listener: View.OnTouchListener?) {
    if (view != null) CommentImages.rememberTouch(view, listener)
}

/**
 * Called right after BaconReader sets the text of a text post, in its detail view and in card mode:
 * the media of its bare links show where the links are, and its images start at full width.
 */
@Export
fun embedPostText(textView: TextView?) {
    if (textView == null) return
    CommentImages.markPostText(textView)
    CommentImages.embedInPlace(textView, null)
}

/**
 * Called right after BaconReader sets the text of an image, GIF or video post, under its media. Its
 * images start at full width.
 */
@Export
fun embed(textView: TextView?) {
    if (textView == null) return
    CommentImages.markPostText(textView)
    CommentImages.embed(textView, null)
}

/**
 * Called in place of the inflate call that makes a row of the post's text in card mode. The row is
 * the TextView alone, so it is put in a vertical LinearLayout, where the images go under it.
 */
@Export
fun inflateCardText(
    inflater: LayoutInflater?,
    resource: Int,
    root: ViewGroup?,
    attachToRoot: Boolean,
): View? {
    val row = inflater?.inflate(resource, root, attachToRoot) ?: return null
    return if (attachToRoot) row else CommentImages.columnFor(row)
}
