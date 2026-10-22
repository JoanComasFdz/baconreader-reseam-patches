// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.editor

import android.text.Editable
import android.widget.EditText
import kotlin.math.max
import kotlin.math.min

/**
 * The buttons of the format bar, on the selection of the text box or at the cursor. Inline formats
 * wrap the selection in markers, or remove them when it already has them; line formats prefix the
 * selected lines.
 */
internal object MarkdownActions {
    // A line of a list, quote or code block.
    private val BLOCK_LINE = Regex("^(\\s*([*-]|\\d+[.)])\\s|\\s*>| {4}|\\t)")

    /** **bold**, *italic*, ~~strikethrough~~, ^(superscript), >!spoiler!<, `code`. */
    fun wrap(editText: EditText, open: String, close: String) {
        val text = editText.text
        var start = min(editText.selectionStart, editText.selectionEnd)
        var end = max(editText.selectionStart, editText.selectionEnd)
        if (start < 0) {
            start = text.length
            end = text.length
        }

        // Already formatted: the markers are removed.
        if (
            start >= open.length &&
                end + close.length <= text.length &&
                text.substring(start - open.length, start) == open &&
                text.substring(end, end + close.length) == close
        ) {
            text.delete(end, end + close.length)
            text.delete(start - open.length, start)
            editText.setSelection(start - open.length, end - open.length)
            return
        }
        text.insert(end, close)
        text.insert(start, open)
        editText.setSelection(start + open.length, end + open.length)
    }

    /**
     * [selected text](url): the cursor goes to the url, or to the text when nothing is selected.
     */
    fun link(editText: EditText) {
        val text = editText.text
        var start = min(editText.selectionStart, editText.selectionEnd)
        var end = max(editText.selectionStart, editText.selectionEnd)
        if (start < 0) {
            start = text.length
            end = text.length
        }
        if (start == end) {
            text.insert(start, "[](https://)")
            editText.setSelection(start + 1)
        } else {
            text.insert(end, "](https://)")
            text.insert(start, "[")
            val url = end + 1 + "](".length
            editText.setSelection(url, url + "https://".length)
        }
    }

    /** # Heading: each tap adds a level, up to ######, then removes it. */
    fun heading(editText: EditText) {
        val text = editText.text
        val lineStart = lineStart(text, max(0, editText.selectionStart))
        var level = 0
        while (lineStart + level < text.length && text[lineStart + level] == '#') level++
        val space = lineStart + level < text.length && text[lineStart + level] == ' '
        when {
            level >= 6 -> text.delete(lineStart, lineStart + level + if (space) 1 else 0)
            level > 0 -> text.insert(lineStart, "#")
            else -> {
                text.insert(lineStart, "# ")
                ensureBlankLineBefore(text, lineStart)
            }
        }
    }

    /**
     * Bullets ("* "), numbers ("1. ", "2. "...), quotes ("> ") or code (" ") on the selected lines,
     * removed when all of them have it. Reddit needs an empty line before a list, quote or code
     * block.
     */
    fun prefixLines(editText: EditText, prefix: String, numbered: Boolean) {
        val text = editText.text
        val start = max(0, min(editText.selectionStart, editText.selectionEnd))
        val end = max(0, max(editText.selectionStart, editText.selectionEnd))
        val first = lineStart(text, start)

        val lines = ArrayList<Int>()
        var line = first
        while (true) {
            lines.add(line)
            val next = text.toString().indexOf('\n', line)
            if (next < 0 || next >= end) break
            line = next + 1
        }
        fun prefixOf(i: Int) = if (numbered) "${i + 1}. " else prefix
        val all = lines.indices.all { startsWith(text, lines[it], prefixOf(it)) }
        // From the last line, so the positions of the others don't move.
        for (i in lines.indices.reversed()) {
            val linePrefix = prefixOf(i)
            if (all) {
                text.delete(lines[i], lines[i] + linePrefix.length)
            } else if (!startsWith(text, lines[i], linePrefix)) {
                text.insert(lines[i], linePrefix)
            }
        }
        if (!all) ensureBlankLineBefore(text, first)
    }

    /** A table or a horizontal rule, on its own lines with empty lines around it. */
    fun block(editText: EditText, block: String) {
        val text = editText.text
        val cursor = max(0, max(editText.selectionStart, editText.selectionEnd))
        val insert = StringBuilder()
        if (cursor > 0 && text[cursor - 1] != '\n') {
            insert.append("\n\n")
        } else if (cursor > 1 && text[cursor - 2] != '\n') {
            insert.append('\n')
        }
        insert.append(block).append("\n\n")
        text.insert(cursor, insert)
        editText.setSelection(cursor + insert.length)
    }

    private fun lineStart(text: CharSequence, index: Int): Int {
        var start = min(index, text.length)
        while (start > 0 && text[start - 1] != '\n') start--
        return start
    }

    private fun startsWith(text: CharSequence, index: Int, prefix: String): Boolean =
        index + prefix.length <= text.length &&
            text.subSequence(index, index + prefix.length).toString() == prefix

    /**
     * Lists, quotes, headings and code need an empty line before them, unless they start the text
     * or continue a list, quote or code block.
     */
    private fun ensureBlankLineBefore(text: Editable, lineStart: Int) {
        if (lineStart == 0) return
        val previousEnd = lineStart - 1 // The '\n' ending the previous line.
        val previous = text.substring(lineStart(text, previousEnd), previousEnd)
        if (previous.trim { it <= ' ' }.isEmpty() || BLOCK_LINE.containsMatchIn(previous)) return
        text.insert(lineStart, "\n")
    }
}
