// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("SelectText")

package com.joancomasfdz.baconreader.selecttext

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.ReplacementSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Menu
import android.widget.ScrollView
import android.widget.TextView
import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.common.ErrorDialog
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.onelouder.baconreader.controlbar.ControlBar
import com.onelouder.baconreader.parser.RedditSpanner
import com.onelouder.baconreader.reddit.Link
import com.onelouder.baconreader.reddit.Message
import com.onelouder.baconreader.reddit.ThingData
import com.onelouder.baconreader.utils.StringUtils
import com.onelouder.baconreader.utils.ThemeHelper
import com.onelouder.baconreader.utils.Utils

private const val TAG = "SelectText"

/** The id the patch adds for the item of the post menu. */
private const val MENU_ITEM = "baconreader_patches_select_text"

/** The id the patch adds for the dialog's text. */
private const val SELECTED_TEXT = "baconreader_patches_selected_text"

/**
 * The app's spoiler: over a box (a BackgroundColorSpan) of the same range, it paints its text the
 * box's color until tapped.
 */
private const val SPOILER_SPAN = "com.onelouder.baconreader.parser.SpoilerClickableSpan"

/** "Show tables" draws a table as this span over one character, which copies as that character. */
private const val TABLE_SPAN = "com.joancomasfdz.baconreader.tables.TableSpan"

/**
 * Called in PostControlBar.updateButtons, the action bar of a comment (and of a post in the list),
 * right after it adds "Copy": adds "Select text" after it, in the same menu.
 */
@Export
fun addButton(bar: ControlBar?, activity: Activity?, thing: ThingData?) {
    if (bar == null || activity == null || thing == null) return
    try {
        val button = bar.setButton(BaconReaderStrings.selectText)
        val icon =
            activity.resources.getIdentifier("ic_controlbar_copy", "attr", activity.packageName)
        if (icon != 0) button.setImage(ThemeHelper.getResourceId(icon))
        button.setOnClickListener { show(activity, thing) }
    } catch (t: Throwable) {
        ErrorDialog.report(TAG, "Could not add Select text to the action bar", t)
    }
}

/**
 * Called in ControlbarBase.drawBar right after it inflates the ⋮ menu of a post: adds "Select text"
 * right after "Copy". Menu items without an order show in the order they were added, so the items
 * after "Copy" are added again after it.
 */
@Export
fun addMenuItem(menu: Menu?, context: Context?) {
    if (menu == null || context == null) return
    try {
        val id = id(context, MENU_ITEM)
        if (id == 0 || menu.findItem(id) != null) return
        val copy = id(context, "action_copy")
        val items = (0 until menu.size()).map(menu::getItem)
        val at = items.indexOfFirst { it.itemId == copy }
        val after = if (at < 0) emptyList() else items.subList(at + 1, items.size)
        val moved = after.map { MovedItem(it.groupId, it.itemId, it.title, it.isVisible) }
        for (item in moved) menu.removeItem(item.id)
        menu.add(Menu.NONE, id, Menu.NONE, BaconReaderStrings.selectText)
        for (item in moved) {
            menu.add(item.group, item.id, Menu.NONE, item.title).isVisible = item.visible
        }
    } catch (t: Throwable) {
        ErrorDialog.report(TAG, "Could not add Select text to the post menu", t)
    }
}

private class MovedItem(val group: Int, val id: Int, val title: CharSequence?, val visible: Boolean)

/**
 * Called first in ControlbarBase.onAction with the id of the tapped item of a post's ⋮ menu: shows
 * the post's text when it's "Select text". Returns whether it was.
 */
@Export
fun onPostAction(itemId: Int, activity: Activity?, link: Link?): Boolean {
    if (activity == null || itemId == 0 || itemId != id(activity, MENU_ITEM)) return false
    if (link != null) show(activity, link)
    return true
}

/**
 * Shows the text of a post (its title, then its text) or of a comment as the app draws it, in a
 * dialog where it can be selected. Its spoilers show, and its links open with a tap.
 */
private fun show(activity: Activity, thing: ThingData) {
    try {
        val text = text(thing) ?: return
        val builder = Utils.getAlertBuilder(activity)
        val context = builder.context
        val padding = dp(context, 24)
        val view =
            TextView(context).apply {
                id = id(context, SELECTED_TEXT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                ThemeHelper.applyFontSize(this)
                val linkColor = resources.getIdentifier("linkColor", "attr", context.packageName)
                if (linkColor != 0) setLinkTextColor(ThemeHelper.getData(linkColor))
                setPadding(padding, padding, padding, 0)
                setTextIsSelectable(true)
                // After setTextIsSelectable, which sets its own: a tap opens a link, and a long
                // press still selects.
                movementMethod = LinkMovementMethod.getInstance()
                setText(text, TextView.BufferType.SPANNABLE)
            }
        val scroll = ScrollView(context).apply { addView(view) }
        builder
            .setView(scroll)
            .setPositiveButton(BaconReaderStrings.selectTextClose, null)
            .create()
            .show()
    } catch (t: Throwable) {
        ErrorDialog.report(TAG, "Could not show the text to select", t)
    }
}

private fun text(thing: ThingData): CharSequence? =
    when (thing) {
        is Message -> body(thing.body_html, thing.subreddit) ?: thing.body
        is Link -> {
            val text = SpannableStringBuilder()
            val title = thing.title
            if (!title.isNullOrEmpty()) {
                // Reddit's titles escape &, < and >, which the app's title unescapes too.
                text.append(
                    StringUtils.unescapeHtml3(title) ?: title,
                    StyleSpan(Typeface.BOLD),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            val body = body(thing.selftext_html, thing.subreddit)
            if (!body.isNullOrEmpty()) {
                if (text.isNotEmpty()) text.append("\n\n")
                text.append(body)
            }
            text.takeIf { it.isNotEmpty() }
        }
        else -> null
    }

/** Reddit's HTML of a post or comment, drawn by the app's own spanner, the same as on screen. */
private fun body(html: String?, subreddit: String?): CharSequence? {
    if (html.isNullOrEmpty()) return null
    val text = SpannableStringBuilder(RedditSpanner(html, subreddit).spannable ?: return null)
    // A table of "Show tables" is drawn over a single character, sized for the comment list: its
    // cells, as text, can be selected and copied instead.
    val tables =
        text.getSpans(0, text.length, ReplacementSpan::class.java).filter {
            it.javaClass.name == TABLE_SPAN
        }
    for (table in tables.sortedByDescending { text.getSpanStart(it) }) {
        val start = text.getSpanStart(table)
        val end = text.getSpanEnd(table)
        text.removeSpan(table)
        text.replace(start, end, table.toString())
    }
    // Spoilers show as plain text, without their box: the text is shown to be selected. A link
    // inside one opens with the first tap, which the spoiler took.
    for (spoiler in text.getSpans(0, text.length, ClickableSpan::class.java)) {
        if (spoiler.javaClass.name != SPOILER_SPAN) continue
        val start = text.getSpanStart(spoiler)
        val end = text.getSpanEnd(spoiler)
        text.removeSpan(spoiler)
        for (box in text.getSpans(start, end, BackgroundColorSpan::class.java)) {
            if (text.getSpanStart(box) == start && text.getSpanEnd(box) == end) text.removeSpan(box)
        }
    }
    return text.trim()
}

private fun id(context: Context, name: String): Int =
    context.resources.getIdentifier(name, "id", context.packageName)

private fun dp(context: Context, value: Int): Int =
    (value * context.resources.displayMetrics.density).toInt()
