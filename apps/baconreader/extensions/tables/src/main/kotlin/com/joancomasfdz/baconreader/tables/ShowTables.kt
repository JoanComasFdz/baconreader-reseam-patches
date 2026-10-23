// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("ShowTables")

package com.joancomasfdz.baconreader.tables

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.view.View
import android.widget.TextView
import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.common.ErrorDialog
import com.onelouder.baconreader.parser.RedditParser

private const val TAG = "ShowTables"

/**
 * Called at the start of RedditSpannerBase.appendTable, after the paragraph break before the table.
 * Appends the table as one character with a [TableSpan] and returns its start, or -1 to let the app
 * flatten the table into "header: value" lines as before.
 */
@Export
fun appendTable(
    spanner: Any?,
    builder: SpannableStringBuilder?,
    element: RedditParser.Element?,
): Int {
    if (spanner == null || builder == null || element == null) return -1
    return try {
        val table = TableParser(spanner).parse(element)
        if (table.columns() == 0 || (table.rows.isEmpty() && table.header.isEmpty())) return -1
        val start = builder.length
        builder.append('￼')
        builder.setSpan(TableSpan(table), start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        start
    } catch (t: Throwable) {
        ErrorDialog.report(TAG, "Could not build the table, using the app's text", t)
        -1
    }
}

/**
 * Called right after the app sets the text of a comment (CommentHolder.updateView) or of a post
 * (DetailLinkFragment.updateSelftext): fits the tables to the view's width.
 */
@Export
fun onTextSet(textView: TextView?) {
    if (textView != null) TableSpan.fitToView(textView)
}

/**
 * Called in the CommentHolder constructor right after the app sets the touch listener of the
 * comment body, which is kept and called for every touch.
 */
@Export
fun installTouch(view: View?, appListener: View.OnTouchListener?) {
    TableTouch.install(view, appListener)
}
