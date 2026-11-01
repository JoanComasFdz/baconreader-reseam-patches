// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("HiddenSpoilers")

package com.joancomasfdz.baconreader.comments

import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.util.Log
import android.view.View
import android.widget.TextView
import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.settings.BaconReaderStrings

private const val TAG = "HiddenSpoilers"
private const val SPOILER = "com.onelouder.baconreader.parser.SpoilerClickableSpan"

/** A span priority above the app's, which sets none. */
private const val FIRST = 100

/**
 * Called right after the app sets the text of a comment or a post, and after a tap shows or hides a
 * spoiler.
 *
 * A link in a spoiler took the tap, which opened it without showing the spoiler: the link is
 * replaced by one that shows the spoiler while it's hidden, and opens the link once it shows.
 *
 * A hidden spoiler is only drawn as a box: accessibility would still read its text. While one is
 * hidden, the view's description is its text with each hidden spoiler read as "spoiler".
 */
@Export
fun describe(view: View?) {
    if (view !is TextView) return
    try {
        guardLinks(view.text as? Spannable)
        val text = view.text as? Spanned
        val hidden =
            text
                ?.getSpans(0, text.length, ClickableSpan::class.java)
                ?.filter { it.javaClass.name == SPOILER && isHidden(it) }
                ?.sortedBy { text.getSpanStart(it) }
        if (text == null || hidden.isNullOrEmpty()) {
            view.contentDescription = null
            return
        }
        val described = StringBuilder()
        var at = 0
        for (spoiler in hidden) {
            val start = text.getSpanStart(spoiler)
            if (start < at) continue
            described.append(text, at, start).append(BaconReaderStrings.spoilerHidden)
            at = text.getSpanEnd(spoiler)
        }
        described.append(text, at, text.length)
        view.contentDescription = described.toString()
    } catch (ex: Exception) {
        Log.i(TAG, "Could not describe the hidden spoilers", ex)
    }
}

/**
 * Puts each link inside a spoiler behind the spoiler, in [text] and in the text the spoiler sets
 * again when tapped.
 */
private fun guardLinks(text: Spannable?) {
    if (text == null) return
    for (spoiler in text.getSpans(0, text.length, ClickableSpan::class.java)) {
        if (spoiler.javaClass.name != SPOILER) continue
        guardLinks(text, spoiler)
        val source = sourceOf(spoiler)
        if (source != null && source !== text) guardLinks(source, spoiler)
    }
}

private fun guardLinks(text: Spannable, spoiler: ClickableSpan) {
    val start = text.getSpanStart(spoiler)
    val end = text.getSpanEnd(spoiler)
    if (start < 0) return
    for (link in text.getSpans(start, end, ClickableSpan::class.java)) {
        if (link === spoiler || link is SpoilerLink || link.javaClass.name == SPOILER) continue
        val linkStart = text.getSpanStart(link)
        val linkEnd = text.getSpanEnd(link)
        if (linkStart < start || linkEnd > end) continue
        text.removeSpan(link)
        // First among the spans: the app's touch handler takes the first one under the finger.
        text.setSpan(
            SpoilerLink(link, spoiler),
            linkStart,
            linkEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or (FIRST shl Spanned.SPAN_PRIORITY_SHIFT),
        )
    }
}

/** A link inside a spoiler: a tap shows the spoiler while it's hidden, and opens the link after. */
private class SpoilerLink(private val link: ClickableSpan, private val spoiler: ClickableSpan) :
    ClickableSpan() {
    override fun onClick(widget: View) {
        if (isHidden(spoiler)) spoiler.onClick(widget) else link.onClick(widget)
    }

    // Drawn before the spoiler, which then hides the link or shows it in its own colors.
    override fun updateDrawState(paint: TextPaint) = link.updateDrawState(paint)
}

// The text the spoiler sets again on the view when tapped. It is private in BaconReader.
private fun sourceOf(spoiler: Any): Spannable? =
    spoiler.javaClass.getDeclaredField("source").apply { isAccessible = true }.get(spoiler)
        as? Spannable

// The span's "hideSpoiler" is false while the spoiler is hidden, and flips with each tap. It is
// private in BaconReader.
private fun isHidden(span: Any): Boolean =
    span.javaClass.getDeclaredField("hideSpoiler").apply { isAccessible = true }.getBoolean(span) ==
        false
