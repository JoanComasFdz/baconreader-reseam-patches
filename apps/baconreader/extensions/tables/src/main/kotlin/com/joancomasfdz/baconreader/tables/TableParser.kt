// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.tables

import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import com.joancomasfdz.baconreader.common.ErrorDialog
import com.onelouder.baconreader.parser.RedditParser
import java.lang.reflect.Method
import java.util.Locale

/**
 * Reads a table element of the app's HTML parser into a [Table]. [spanner] is the app's
 * RedditSpanner building the text, which makes the cells' links.
 */
internal class TableParser(private val spanner: Any) {
    // RedditSpanner's own appendElementInternal, protected, so it is called through reflection.
    private val appendElement: Method? by lazy {
        try {
            findMethod(
                spanner.javaClass,
                "appendElementInternal",
                SpannableStringBuilder::class.java,
                RedditParser.Element::class.java,
            )
        } catch (t: Throwable) {
            ErrorDialog.report(TAG, "App links not found, using the plain URLs", t)
            null
        }
    }

    fun parse(element: RedditParser.Element): Table {
        val table = Table()
        val thead = element.selectSingleNode("thead")
        val tbody = element.selectSingleNode("tbody") ?: element

        thead?.selectSingleNode("tr")?.selectChildren("th")?.forEach { cell ->
            table.header.add(cellText(cell))
            table.alignments.add(alignment(cell.getAttribute("align")))
        }
        for (row in tbody.selectChildren("tr").orEmpty()) {
            val cells =
                row.selectChildren("td").orEmpty().ifEmpty { row.selectChildren("th").orEmpty() }
            val texts = ArrayList<CharSequence>()
            cells.forEachIndexed { i, cell ->
                texts.add(cellText(cell))
                if (i >= table.alignments.size) {
                    table.alignments.add(alignment(cell.getAttribute("align")))
                }
            }
            table.rows.add(texts)
        }
        return table
    }

    /** The cell's text with its links, bold, italic, strikethrough and code. */
    private fun cellText(cell: RedditParser.Element): CharSequence {
        val out = SpannableStringBuilder()
        appendNode(out, cell)
        return out.trimWhitespace()
    }

    private fun appendNode(out: SpannableStringBuilder, node: Any?) {
        if (node is RedditParser.Text) {
            out.append(node.text.orEmpty().replace(WHITESPACE, " "))
            return
        }
        if (node !is RedditParser.Element) return
        val tag = node.name?.lowercase(Locale.ROOT).orEmpty()
        if (tag == "br") {
            out.append('\n')
            return
        }
        val start = out.length
        node.children?.forEach { appendNode(out, it) }
        val end = out.length
        if (end <= start) return
        val span: Any? =
            when (tag) {
                "a" -> appLink(node) ?: node.getAttribute("href")?.let { URLSpan(absolute(it)) }
                "strong",
                "b" -> StyleSpan(Typeface.BOLD)
                "em",
                "i" -> StyleSpan(Typeface.ITALIC)
                "del",
                "s",
                "strike" -> StrikethroughSpan()
                "code" -> TypefaceSpan("monospace")
                else -> null
            }
        if (span != null) out.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /**
     * The link span the app makes for an <a> (in-app browser, subreddit and user links,
     * spoilers...), so tapping a link in a table does what it does anywhere else. Null if unknown.
     */
    private fun appLink(anchor: RedditParser.Element): ClickableSpan? {
        val append = appendElement ?: return null
        return try {
            val scratch = SpannableStringBuilder()
            append.invoke(spanner, scratch, anchor)
            scratch.getSpans(0, scratch.length, ClickableSpan::class.java).firstOrNull()
        } catch (t: Throwable) {
            ErrorDialog.report(TAG, "App link failed, using the plain URL", t)
            null
        }
    }

    private companion object {
        const val TAG = "ShowTables"
        val WHITESPACE = Regex("\\s+")

        fun alignment(align: String?): Layout.Alignment =
            when (align?.trim()?.lowercase(Locale.ROOT)) {
                "center" -> Layout.Alignment.ALIGN_CENTER
                "right" -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_NORMAL
            }

        fun absolute(href: String): String =
            if (href.startsWith("/")) "https://www.reddit.com$href" else href

        fun SpannableStringBuilder.trimWhitespace(): CharSequence {
            while (isNotEmpty() && Character.isWhitespace(this[length - 1])) delete(
                length - 1,
                length,
            )
            while (isNotEmpty() && Character.isWhitespace(this[0])) delete(0, 1)
            return this
        }

        fun findMethod(type: Class<*>, name: String, vararg parameters: Class<*>): Method {
            var c: Class<*>? = type
            while (c != null) {
                try {
                    return c.getDeclaredMethod(name, *parameters).apply { isAccessible = true }
                } catch (_: NoSuchMethodException) {}
                c = c.superclass
            }
            throw NoSuchMethodException(name)
        }
    }
}
