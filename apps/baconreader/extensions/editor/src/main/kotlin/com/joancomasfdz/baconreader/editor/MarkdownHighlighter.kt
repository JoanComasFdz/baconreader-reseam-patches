// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.editor

import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import java.util.regex.Pattern

/**
 * Shows Reddit's markdown formatted while typing, as WhatsApp does: the markers (**, ~~, # ...)
 * stay in the text, dimmed, and the text and its markers get the format. Only the look changes: the
 * text sent is the same.
 */
internal class MarkdownHighlighter(textColor: Int, private val accent: Int) {
    /** The spans of the highlighter, removed before each update. The user's own text has none. */
    private interface Markdown

    private class Bold : StyleSpan(Typeface.BOLD), Markdown

    private class Italic : StyleSpan(Typeface.ITALIC), Markdown

    private class Strike : StrikethroughSpan(), Markdown

    private class Mono : TypefaceSpan("monospace"), Markdown

    private class Background(color: Int) : BackgroundColorSpan(color), Markdown

    private class Foreground(color: Int) : ForegroundColorSpan(color), Markdown

    private class Size(proportion: Float) : RelativeSizeSpan(proportion), Markdown

    private class Superscript : SuperscriptSpan(), Markdown

    private class Underline : UnderlineSpan(), Markdown

    private val dim = withAlpha(textColor, 0x80)
    private val codeBackground = withAlpha(textColor, 0x1F)
    private val spoilerBackground = withAlpha(textColor, 0x45)

    fun highlight(text: Editable) {
        for (span in text.getSpans(0, text.length, Markdown::class.java)) text.removeSpan(span)
        val string = text.toString()
        var inFence = false
        var lineStart = 0
        while (lineStart <= string.length) {
            var lineEnd = string.indexOf('\n', lineStart)
            if (lineEnd < 0) lineEnd = string.length
            val line = string.substring(lineStart, lineEnd)

            if (FENCE.matcher(line).matches()) {
                code(text, lineStart, lineEnd)
                inFence = !inFence
            } else if (inFence || INDENTED_CODE.matcher(line).matches()) {
                code(text, lineStart, lineEnd)
            } else {
                line(text, line, lineStart, lineEnd)
                inline(text, line, lineStart)
            }
            lineStart = lineEnd + 1
        }
    }

    private fun code(text: Editable, start: Int, end: Int) {
        if (start >= end) return
        set(text, Mono(), start, end)
        set(text, Background(codeBackground), start, end)
    }

    private fun line(text: Editable, line: String, start: Int, end: Int) {
        if (start >= end) return
        val heading = HEADING.matcher(line)
        if (heading.matches()) {
            val level = heading.group(1)!!.length
            set(text, Size(HEADING_SIZES[level - 1]), start, end)
            set(text, Bold(), start, end)
            set(text, Foreground(dim), start, start + level)
            return
        }
        val quote = QUOTE.matcher(line)
        if (quote.matches()) {
            set(text, Italic(), start, end)
            set(text, Foreground(dim), start + quote.start(1), start + quote.end(1))
            return
        }
        if (RULE.matcher(line).matches()) {
            set(text, Foreground(dim), start, end)
            return
        }
        val list = LIST.matcher(line)
        if (list.find()) {
            set(text, Foreground(accent), start + list.start(1), start + list.end(1))
            set(text, Bold(), start + list.start(1), start + list.end(1))
            return
        }
        if (TABLE.matcher(line).matches()) {
            set(text, Mono(), start, end)
            line.forEachIndexed { i, c ->
                if (c == '|' || c == '-' || c == ':')
                    set(text, Foreground(dim), start + i, start + i + 1)
            }
        }
    }

    private fun inline(text: Editable, line: String, offset: Int) {
        // Inline code first: nothing inside it is formatted.
        val code = ArrayList<IntRange>()
        var matcher = CODE.matcher(line)
        while (matcher.find()) {
            code.add(matcher.start() until matcher.end())
            code(text, offset + matcher.start(), offset + matcher.end())
            dim(text, offset + matcher.start(), 1)
            dim(text, offset + matcher.end() - 1, 1)
        }

        matcher = LINK.matcher(line)
        while (matcher.find()) {
            if (inCode(code, matcher.start())) continue
            val start = offset + matcher.start()
            set(text, Foreground(accent), start + 1, offset + matcher.end(1))
            set(text, Underline(), start + 1, offset + matcher.end(1))
            dim(text, start, 1)
            dim(text, offset + matcher.end(1), matcher.end() - matcher.end(1))
        }

        delimited(text, line, offset, code, BOLD_ITALIC, 3) { listOf(Bold(), Italic()) }
        delimited(text, line, offset, code, BOLD, 2) { listOf(Bold()) }
        delimited(text, line, offset, code, ITALIC_STAR, 1) { listOf(Italic()) }
        delimited(text, line, offset, code, ITALIC_UNDERSCORE, 1) { listOf(Italic()) }
        delimited(text, line, offset, code, STRIKE, 2) { listOf(Strike()) }
        delimited(text, line, offset, code, SPOILER, 2) { listOf(Background(spoilerBackground)) }

        matcher = SUPERSCRIPT_GROUP.matcher(line)
        while (matcher.find()) {
            if (inCode(code, matcher.start())) continue
            val start = offset + matcher.start()
            val end = offset + matcher.end()
            set(text, Superscript(), start, end)
            set(text, Size(0.75f), start, end)
            dim(text, start, 2)
            dim(text, end - 1, 1)
        }
        matcher = SUPERSCRIPT_WORD.matcher(line)
        while (matcher.find()) {
            if (inCode(code, matcher.start())) continue
            val start = offset + matcher.start()
            set(text, Superscript(), start, offset + matcher.end())
            set(text, Size(0.75f), start, offset + matcher.end())
            dim(text, start, 1)
        }
    }

    /** Text between equal markers: the markers dimmed, the whole match formatted. */
    private fun delimited(
        text: Editable,
        line: String,
        offset: Int,
        code: List<IntRange>,
        pattern: Pattern,
        markerLength: Int,
        spans: () -> List<Any>,
    ) {
        val matcher = pattern.matcher(line)
        while (matcher.find()) {
            if (inCode(code, matcher.start())) continue
            val start = offset + matcher.start()
            val end = offset + matcher.end()
            for (span in spans()) set(text, span, start, end)
            dim(text, start, markerLength)
            dim(text, end - markerLength, markerLength)
        }
    }

    private fun dim(text: Editable, start: Int, length: Int) {
        set(text, Foreground(dim), start, start + length)
    }

    private companion object {
        val HEADING_SIZES = floatArrayOf(1.6f, 1.45f, 1.3f, 1.2f, 1.1f, 1f)

        // Lines.
        val FENCE: Pattern = Pattern.compile("^\\s*```.*$")
        val INDENTED_CODE: Pattern = Pattern.compile("^( {4}|\\t).*$")
        val HEADING: Pattern = Pattern.compile("^(#{1,6})\\s+.*$")
        val QUOTE: Pattern = Pattern.compile("^\\s*(>+)(?!!).*$")
        val LIST: Pattern = Pattern.compile("^\\s*([*-]|\\d+[.)])\\s")
        val RULE: Pattern = Pattern.compile("^\\s*([-*_])\\s*(\\1\\s*){2,}$")
        val TABLE: Pattern = Pattern.compile("^\\s*\\|.*$")

        // Inline, in this order.
        val CODE: Pattern = Pattern.compile("`[^`\\n]+`")
        val LINK: Pattern = Pattern.compile("\\[([^\\]\\n]+)\\]\\(([^)\\s]*)\\)")
        val BOLD_ITALIC: Pattern = Pattern.compile("(\\*\\*\\*|___)(?=\\S)([^\\n]+?)(?<=\\S)\\1")
        val BOLD: Pattern = Pattern.compile("(\\*\\*|__)(?=\\S)([^\\n]+?)(?<=\\S)\\1")
        val ITALIC_STAR: Pattern =
            Pattern.compile("(?<![*\\\\])\\*(?=[^\\s*])([^\\n*]+?)(?<=[^\\s*])\\*(?!\\*)")
        val ITALIC_UNDERSCORE: Pattern =
            Pattern.compile("(?<![\\w_\\\\])_(?=[^\\s_])([^\\n_]+?)(?<=[^\\s_])_(?![\\w_])")
        val STRIKE: Pattern = Pattern.compile("~~(?=\\S)([^\\n]+?)(?<=\\S)~~")
        val SPOILER: Pattern = Pattern.compile(">!([^\\n]+?)!<")
        val SUPERSCRIPT_GROUP: Pattern = Pattern.compile("\\^\\(([^)\\n]+)\\)")
        val SUPERSCRIPT_WORD: Pattern = Pattern.compile("\\^([^\\s^(][^\\s^]*)")

        fun withAlpha(color: Int, alpha: Int): Int =
            Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

        fun inCode(code: List<IntRange>, index: Int): Boolean = code.any { index in it }

        fun set(text: Editable, span: Any, start: Int, end: Int) {
            if (start < 0 || end > text.length || start >= end) return
            text.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
