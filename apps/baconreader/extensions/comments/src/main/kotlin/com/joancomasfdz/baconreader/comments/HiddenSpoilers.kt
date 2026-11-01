// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("HiddenSpoilers")

package com.joancomasfdz.baconreader.comments

import android.text.Spanned
import android.text.style.ClickableSpan
import android.util.Log
import android.view.View
import android.widget.TextView
import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.settings.BaconReaderStrings

private const val TAG = "HiddenSpoilers"
private const val SPOILER = "com.onelouder.baconreader.parser.SpoilerClickableSpan"

/**
 * Called right after the app sets the text of a comment or a post, and after a tap shows or hides a
 * spoiler. A hidden spoiler is only drawn as a box: accessibility would still read its text. While
 * one is hidden, the view's description is its text with each hidden spoiler read as "spoiler".
 */
@Export
fun describe(view: View?) {
    if (view !is TextView) return
    try {
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

// The span's "hideSpoiler" is false while the spoiler is hidden, and flips with each tap. It is
// private in BaconReader.
private fun isHidden(span: Any): Boolean =
    span.javaClass.getDeclaredField("hideSpoiler").apply { isAccessible = true }.getBoolean(span) ==
        false
