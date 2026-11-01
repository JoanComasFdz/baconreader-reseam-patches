// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.tables

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.ReplacementSpan
import android.text.style.StyleSpan
import android.widget.TextView
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import java.util.Collections
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A parsed table: header cells (may be empty), body rows, and the column alignments. */
internal class Table {
    val header = ArrayList<CharSequence>()
    val rows = ArrayList<List<CharSequence>>()
    val alignments = ArrayList<Layout.Alignment>()

    fun columns(): Int = rows.fold(header.size) { n, row -> max(n, row.size) }

    fun alignment(column: Int): Layout.Alignment =
        alignments.getOrNull(column) ?: Layout.Alignment.ALIGN_NORMAL
}

/**
 * Reddit tables, drawn as a grid inside the text instead of the app's "header: value" lines. The
 * table takes one character of the text. Wide tables can be expanded and scrolled sideways, and
 * links in cells can be tapped (see [TableTouch]).
 */
/**
 * A cell of a table, or its expand or fit button, as accessibility shows it: its [text], its
 * [bounds] relative to the table's top left (a cell's text's; empty when out of sight) and the
 * [link] a tap on it opens, if any.
 */
internal class TablePart(
    val text: CharSequence,
    val bounds: RectF,
    val link: ClickableSpan?,
    val isButton: Boolean = false,
)

internal class TableSpan(private val table: Table) : ReplacementSpan() {
    private val density = Resources.getSystem().displayMetrics.density
    private val key = key(table)

    // Until the text is laid out, nearly the whole screen width.
    private var maxWidth = Resources.getSystem().displayMetrics.widthPixels - dp(40)

    /** Set when the TextView handles table touches (expand button, horizontal scroll). */
    private var interactive = false
    private var scrollX = 0f

    // Layout cache, rebuilt when the width, text size, color or mode changes.
    private var cachedWidth = -1
    private var cachedTextSize = -1f
    private var cachedColor = 0
    private var cachedExpanded = false
    private var cachedInteractive = false
    private var layouts: Array<Array<StaticLayout?>>? = null
    private var columnWidths = IntArray(0)
    private var rowHeights = IntArray(0)
    private var canExpand = false
    private var gridWidth = 0
    private var visibleWidth = 0
    private var scrollbarHeight = 0
    private var padH = 0
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    var gridHeight = 0
        private set

    /** The height of the grid and the footer below it. */
    var height = 0
        private set

    /** The expand/fit button, relative to the table's top left, or empty when there is none. */
    val toggleRect = RectF()

    private val isExpanded: Boolean
        get() = EXPANDED[key] == true

    /** Switches between fitting the width and natural column widths (scrolled sideways). */
    fun toggle() {
        EXPANDED[key] = !isExpanded
        scrollX = 0f
    }

    /** Scrolls an expanded table; returns whether it moved. */
    fun scrollBy(dx: Float): Boolean {
        val limit = max(0, gridWidth - visibleWidth).toFloat()
        val next = max(0f, min(limit, scrollX + dx))
        if (next == scrollX) return false
        scrollX = next
        return true
    }

    fun canScroll(): Boolean = isExpanded && gridWidth > visibleWidth

    /**
     * The link under a point of the grid (relative to the table's top left, as drawn), or null.
     * Only the text of the link counts, not the empty space around it in the cell.
     */
    fun linkAt(x: Float, y: Float): ClickableSpan? {
        val layouts = layouts ?: return null
        if (y < 0 || y > gridHeight || x < 0 || x > visibleWidth) return null
        val border = max(1f, density).toInt()
        val gridX = x + scrollX
        var rowTop = 0f
        for (r in rowHeights.indices) {
            if (y < rowTop + rowHeights[r]) {
                var cellLeft = border.toFloat()
                for (c in columnWidths.indices) {
                    val cellRight = cellLeft + columnWidths[c] + 2 * padH
                    if (gridX < cellRight) {
                        return linkInCell(
                            layouts[r][c],
                            gridX - cellLeft - padH,
                            y - rowTop - dp(PAD_V_DP) - border,
                        )
                    }
                    cellLeft = cellRight + border
                }
                return null
            }
            rowTop += rowHeights[r]
        }
        return null
    }

    /**
     * What accessibility shows of the table: each cell, row by row, then the expand or fit button,
     * with where each shows, relative to the table's top left: a cell's text, where a tap opens its
     * link. A cell scrolled out of the table's width, or empty, has empty bounds.
     */
    fun parts(): List<TablePart> {
        val layouts = layouts ?: return emptyList()
        val border = max(1f, density)
        val shown = RectF(0f, 0f, visibleWidth.toFloat(), gridHeight.toFloat())
        val parts = ArrayList<TablePart>()
        var rowTop = 0f
        for (r in rowHeights.indices) {
            var cellLeft = border - scrollX
            for (c in columnWidths.indices) {
                val cellRight = cellLeft + columnWidths[c] + 2 * padH
                val layout = layouts[r][c]
                val bounds = RectF()
                if (layout != null) {
                    var left = Float.MAX_VALUE
                    var right = 0f
                    for (line in 0 until layout.lineCount) {
                        left = min(left, layout.getLineLeft(line))
                        right = max(right, layout.getLineRight(line))
                    }
                    val textLeft = cellLeft + padH
                    val textTop = rowTop + dp(PAD_V_DP) + border
                    bounds.set(textLeft + left, textTop, textLeft + right, textTop + layout.height)
                }
                if (bounds.isEmpty || !bounds.intersect(shown)) bounds.setEmpty()
                val text = layout?.text ?: ""
                val link = (text as? Spanned)?.getSpans(0, text.length, ClickableSpan::class.java)
                parts.add(TablePart(text, bounds, link?.firstOrNull()))
                cellLeft = cellRight + border
            }
            rowTop += rowHeights[r]
        }
        if (!toggleRect.isEmpty) parts.add(TablePart(label(), RectF(toggleRect), null, true))
        return parts
    }

    private fun linkInCell(layout: StaticLayout?, x: Float, y: Float): ClickableSpan? {
        val text = layout?.text as? Spanned ?: return null
        val slack = dp(6) // Fingers are wider than text.
        if (y < -slack || y > layout.height + slack) return null
        val line = layout.getLineForVertical(max(0f, min(y, (layout.height - 1).toFloat())).toInt())
        if (x < layout.getLineLeft(line) - slack || x > layout.getLineRight(line) + slack) {
            return null
        }
        val offset = layout.getOffsetForHorizontal(line, x)
        val links =
            text.getSpans(
                max(0, offset - 1),
                min(text.length, offset + 1),
                ClickableSpan::class.java,
            )
        return links.firstOrNull()
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        ensureLayout(paint)
        if (fm != null) {
            fm.ascent = -height
            fm.top = -height
            fm.descent = dp(MARGIN_BELOW_DP)
            fm.bottom = dp(MARGIN_BELOW_DP)
        }
        return visibleWidth
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        ensureLayout(paint)
        val color = paint.color
        val tableTop = (y - height).toFloat()

        canvas.save()
        canvas.clipRect(x, tableTop, x + visibleWidth, tableTop + gridHeight)
        drawGrid(canvas, x - scrollX, tableTop, color)
        canvas.restore()

        val footerTop = tableTop + gridHeight
        if (scrollbarHeight > 0) {
            val barTop = footerTop + dp(3)
            val barHeight = dp(3).toFloat()
            fill.color = withAlpha(color, 0x1A)
            canvas.drawRoundRect(
                x,
                barTop,
                x + visibleWidth,
                barTop + barHeight,
                barHeight,
                barHeight,
                fill,
            )
            val thumb = max(dp(24).toFloat(), visibleWidth.toFloat() * visibleWidth / gridWidth)
            val offset =
                if (gridWidth > visibleWidth) {
                    scrollX / (gridWidth - visibleWidth) * (visibleWidth - thumb)
                } else 0f
            fill.color = withAlpha(color, 0x80)
            canvas.drawRoundRect(
                x + offset,
                barTop,
                x + offset + thumb,
                barTop + barHeight,
                barHeight,
                barHeight,
                fill,
            )
        }
        if (!toggleRect.isEmpty) {
            labelPaint.set(paint)
            labelPaint.textSize = paint.textSize * 0.8f
            labelPaint.color = withAlpha(color, 0xCC)
            val pillLeft = x + toggleRect.left
            val pillTop = tableTop + toggleRect.top
            fill.color = withAlpha(color, 0x40)
            fill.style = Paint.Style.STROKE
            fill.strokeWidth = max(1f, density)
            val radius = toggleRect.height() / 2f
            canvas.drawRoundRect(
                pillLeft,
                pillTop,
                pillLeft + toggleRect.width(),
                pillTop + toggleRect.height(),
                radius,
                radius,
                fill,
            )
            fill.style = Paint.Style.FILL
            val metrics = labelPaint.fontMetrics
            val baseline = pillTop + (toggleRect.height() - metrics.ascent - metrics.descent) / 2f
            canvas.drawText(label(), pillLeft + dp(10), baseline, labelPaint)
        }
    }

    private fun drawGrid(canvas: Canvas, left: Float, tableTop: Float, color: Int) {
        val layouts = layouts ?: return
        val border = max(1f, density)
        val hasHeader = table.header.isNotEmpty()

        // Header and striped rows.
        var rowTop = tableTop
        for (r in rowHeights.indices) {
            val alpha =
                when {
                    hasHeader && r == 0 -> 0x24
                    r % 2 == (if (hasHeader) 0 else 1) -> 0x0E
                    else -> 0
                }
            if (alpha > 0) {
                fill.color = withAlpha(color, alpha)
                canvas.drawRect(left, rowTop, left + gridWidth, rowTop + rowHeights[r], fill)
            }
            rowTop += rowHeights[r]
        }

        // Grid lines.
        fill.color = withAlpha(color, 0x40)
        var lineY = tableTop
        for (r in 0..rowHeights.size) {
            canvas.drawRect(left, lineY, left + gridWidth, lineY + border, fill)
            if (r < rowHeights.size) lineY += rowHeights[r]
        }
        var lineX = left
        for (c in 0..columnWidths.size) {
            canvas.drawRect(lineX, tableTop, lineX + border, tableTop + gridHeight, fill)
            if (c < columnWidths.size) lineX += columnWidths[c] + 2 * padH + border
        }

        // Cells.
        rowTop = tableTop
        for (r in layouts.indices) {
            var cellLeft = left + border
            for (c in columnWidths.indices) {
                val layout = layouts[r][c]
                if (layout != null) {
                    canvas.save()
                    canvas.translate(cellLeft + padH, rowTop + dp(PAD_V_DP) + border)
                    layout.draw(canvas)
                    canvas.restore()
                }
                cellLeft += columnWidths[c] + 2 * padH + border
            }
            rowTop += rowHeights[r]
        }
    }

    private fun ensureLayout(paint: Paint) {
        val expanded = isExpanded
        if (
            layouts != null &&
                cachedWidth == maxWidth &&
                cachedTextSize == paint.textSize &&
                cachedColor == paint.color &&
                cachedExpanded == expanded &&
                cachedInteractive == interactive
        ) {
            return
        }
        cachedWidth = maxWidth
        cachedTextSize = paint.textSize
        cachedColor = paint.color
        cachedExpanded = expanded
        cachedInteractive = interactive

        val textPaint = TextPaint(paint)
        if (paint is TextPaint) textPaint.linkColor = paint.linkColor
        val columns = table.columns()
        val cells = ArrayList<Array<CharSequence?>>()
        if (table.header.isNotEmpty()) cells.add(bold(table.header, columns))
        for (row in table.rows) cells.add(Array(columns) { row.getOrNull(it) })

        val natural = IntArray(columns)
        val longestWord = IntArray(columns)
        for (row in cells) {
            for (c in 0 until columns) {
                val cell = row[c] ?: continue
                // Widest line, with the cell's bold and monospace spans taken into account.
                var lineStart = 0
                for (i in 0..cell.length) {
                    if (i == cell.length || cell[i] == '\n') {
                        val width = Layout.getDesiredWidth(cell, lineStart, i, textPaint)
                        natural[c] = max(natural[c], ceil(width).toInt() + 1)
                        lineStart = i + 1
                    }
                }
                // Longest word, so narrow columns wrap between words, not inside them.
                var wordStart = 0
                for (i in 0..cell.length) {
                    if (i == cell.length || Character.isWhitespace(cell[i])) {
                        if (i > wordStart) {
                            val width = Layout.getDesiredWidth(cell, wordStart, i, textPaint)
                            longestWord[c] = max(longestWord[c], ceil(width).toInt() + 1)
                        }
                        wordStart = i + 1
                    }
                }
            }
        }
        val border = max(1f, density).toInt()
        // Many columns on a narrow screen: less padding, so the table still fits the width.
        padH = dp(PAD_H_DP)
        if (maxWidth / max(1, columns) < dp(64)) padH = dp(3)
        val available = maxWidth - columns * 2 * padH - (columns + 1) * border
        canExpand = natural.sum() > available
        columnWidths =
            if (expanded && canExpand) {
                // Natural widths, only very long cells wrap.
                val cap = max(dp(MAX_EXPANDED_COLUMN_DP), (maxWidth * 0.9f).toInt())
                IntArray(columns) { max(1, min(natural[it], cap)) }
            } else {
                // Never wider than the view when not expanded: the minimum shrinks with many
                // columns.
                val minimum = min(dp(MIN_COLUMN_DP), max(1, available / max(1, columns)))
                fillWidth(columnWidths(natural, longestWord, available, minimum), available)
            }

        val built = Array(cells.size) { arrayOfNulls<StaticLayout>(columns) }
        rowHeights = IntArray(cells.size)
        for (r in cells.indices) {
            var rowHeight = 0
            for (c in 0 until columns) {
                val layout =
                    cellLayout(
                        cells[r][c] ?: "",
                        textPaint,
                        max(1, columnWidths[c]),
                        table.alignment(c),
                    )
                built[r][c] = layout
                rowHeight = max(rowHeight, layout.height)
            }
            rowHeights[r] = rowHeight + 2 * dp(PAD_V_DP) + border
        }
        layouts = built
        gridWidth = border + columnWidths.sumOf { it + 2 * padH + border }
        gridHeight = border + rowHeights.sum()
        visibleWidth = min(gridWidth, maxWidth)
        scrollX = max(0f, min(scrollX, (gridWidth - visibleWidth).toFloat()))

        // Footer: the scrollbar (expanded and wider than the view) and the expand/fit button.
        scrollbarHeight = if (expanded && canExpand && gridWidth > visibleWidth) dp(9) else 0
        toggleRect.setEmpty()
        var footer = scrollbarHeight
        if (interactive && canExpand) {
            labelPaint.set(paint)
            labelPaint.textSize = paint.textSize * 0.8f
            val pillWidth = labelPaint.measureText(label()) + dp(20)
            val pillHeight = dp(26).toFloat()
            val pillTop = (gridHeight + footer + dp(4)).toFloat()
            toggleRect.set(
                visibleWidth - pillWidth,
                pillTop,
                visibleWidth.toFloat(),
                pillTop + pillHeight,
            )
            footer += dp(4) + pillHeight.toInt()
        }
        height = gridHeight + footer
    }

    private fun label(): String =
        if (isExpanded) BaconReaderStrings.tablesFitToScreen else BaconReaderStrings.tablesExpand

    fun dp(value: Int): Int = (value * density).roundToInt()

    companion object {
        private const val PAD_H_DP = 6
        private const val PAD_V_DP = 4
        private const val MIN_COLUMN_DP = 40
        private const val MARGIN_BELOW_DP = 4

        /** In expanded mode, cells wider than this (or 90% of the view) still wrap. */
        private const val MAX_EXPANDED_COLUMN_DP = 320

        /**
         * Tables expanded by the user, by content, so the state survives the list recycling views.
         */
        private val EXPANDED: MutableMap<String, Boolean> =
            Collections.synchronizedMap(HashMap<String, Boolean>())

        /**
         * Sizes the tables of a comment or post text to the TextView's real width, and lets the
         * view handle table touches (expand button, horizontal scrolling).
         */
        fun fitToView(textView: TextView) {
            val text = textView.text as? Spanned ?: return
            val spans = text.getSpans(0, text.length, TableSpan::class.java)
            if (spans.isEmpty()) return
            TableTouch.install(textView, null)
            TableAccessibility.install(textView)
            for (span in spans) span.interactive = true
            relayout(textView)
            textView.post {
                val width = textView.width - textView.totalPaddingLeft - textView.totalPaddingRight
                val current = textView.text as? Spanned
                if (width > 0 && current != null) {
                    for (span in current.getSpans(0, current.length, TableSpan::class.java)) {
                        span.maxWidth = width
                    }
                    relayout(textView)
                }
            }
        }

        /** Makes the TextView measure the tables again, after their size or mode changed. */
        fun relayout(textView: TextView) {
            val current = textView.text
            if (current is Spannable) {
                // Setting the span again makes the TextView lay the line out again.
                for (span in current.getSpans(0, current.length, TableSpan::class.java)) {
                    val start = current.getSpanStart(span)
                    val end = current.getSpanEnd(span)
                    val flags = current.getSpanFlags(span)
                    current.removeSpan(span)
                    current.setSpan(span, start, end, flags)
                }
                textView.requestLayout()
                textView.invalidate()
            } else if (current != null) {
                textView.text = current
            }
        }

        /**
         * StaticLayout.Builder needs Android 6; the constructor is deprecated but works everywhere.
         */
        @Suppress("DEPRECATION")
        private fun cellLayout(
            text: CharSequence,
            paint: TextPaint,
            width: Int,
            alignment: Layout.Alignment,
        ): StaticLayout =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                    .setAlignment(alignment)
                    .setIncludePad(false)
                    .build()
            } else {
                StaticLayout(text, 0, text.length, paint, width, alignment, 1f, 0f, false)
            }

        private fun key(table: Table): String = buildString {
            for (cell in table.header) append(cell).append('|')
            for (row in table.rows) {
                append('\n')
                for (cell in row) append(cell).append('|')
            }
        }

        /**
         * Narrower tables are stretched to the full width, sharing the space in proportion to the
         * columns.
         */
        private fun fillWidth(widths: IntArray, available: Int): IntArray {
            val sum = widths.sum()
            if (sum <= 0 || sum >= available) return widths
            val result = IntArray(widths.size) { (widths[it].toLong() * available / sum).toInt() }
            result[widths.size - 1] += available - result.sum() // Rounding leftovers.
            return result
        }

        /**
         * Column widths within the available width: every column gets its longest word, and the
         * space left goes where the text is longer.
         */
        private fun columnWidths(
            natural: IntArray,
            longestWord: IntArray,
            available: Int,
            minimum: Int,
        ): IntArray {
            val words = longestWord.sum()
            // Words must break anyway.
            if (words > available) return columnWidths(natural, available, minimum)
            val extra = IntArray(natural.size) { max(0, natural[it] - longestWord[it]) }
            val extraWidths = columnWidths(extra, available - words, 0)
            return IntArray(natural.size) { longestWord[it] + extraWidths[it] }
        }

        /**
         * Columns that fit their fair share keep their natural width; the space left is shared by
         * the wider ones, which wrap their text.
         */
        private fun columnWidths(natural: IntArray, available: Int, minimum: Int): IntArray {
            val n = natural.size
            if (natural.sum() <= available) return natural.copyOf()
            val widths = IntArray(n)
            val fixed = BooleanArray(n)
            var remaining = available
            var open = n
            var changed = true
            while (changed && open > 0) {
                changed = false
                val share = remaining / open
                for (c in 0 until n) {
                    if (!fixed[c] && natural[c] <= share) {
                        widths[c] = natural[c]
                        fixed[c] = true
                        remaining -= natural[c]
                        open--
                        changed = true
                    }
                }
            }
            val share = if (open > 0) remaining / open else 0
            for (c in 0 until n) if (!fixed[c]) widths[c] = max(share, minimum)
            return widths
        }

        private fun bold(header: List<CharSequence>, columns: Int): Array<CharSequence?> =
            Array(columns) { c ->
                header.getOrNull(c)?.let {
                    SpannableString(it).apply {
                        setSpan(
                            StyleSpan(Typeface.BOLD),
                            0,
                            length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                    }
                }
            }

        private fun withAlpha(color: Int, alpha: Int): Int =
            (color and 0x00FFFFFF) or (alpha shl 24)
    }
}
