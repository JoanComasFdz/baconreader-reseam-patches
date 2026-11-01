// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.tables

import android.text.Spanned
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import kotlin.math.abs

/**
 * Touches on tables drawn in a TextView: tap a link in a cell to open it, tap the expand or fit
 * button, drag an expanded table sideways. Everything else goes to the view's own listener, if any,
 * and to the TextView as before.
 */
internal class TableTouch
private constructor(view: View, private val delegate: View.OnTouchListener?) :
    View.OnTouchListener {
    private val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
    private var active: TableSpan? = null
    private var tableLeft = 0f
    private var tableTop = 0f
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var dragging = false

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val handledByDelegate = delegate?.onTouch(view, event) == true
        if (view !is TextView) return handledByDelegate

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                active = findTable(view, event.x, event.y)
                dragging = false
                downX = event.x
                lastX = event.x
                downY = event.y
                return active != null || handledByDelegate
            }
            MotionEvent.ACTION_MOVE -> {
                val span = active ?: return handledByDelegate
                val dx = event.x - downX
                val dy = event.y - downY
                if (!dragging && span.canScroll() && abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                    dragging = true
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (dragging) {
                    if (span.scrollBy(lastX - event.x)) {
                        view.invalidate()
                        // The cells moved: accessibility reads where they are again.
                        view.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                    }
                    lastX = event.x
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val span = active ?: return handledByDelegate
                active = null
                if (
                    !dragging &&
                        abs(event.x - downX) < touchSlop &&
                        abs(event.y - downY) < touchSlop
                ) {
                    val x = event.x - tableLeft
                    val y = event.y - tableTop
                    if (!span.toggleRect.isEmpty && onButton(span, x, y)) {
                        span.toggle()
                        TableSpan.relayout(view)
                    } else if (y <= span.gridHeight) {
                        span.linkAt(x, y)?.onClick(view)
                    }
                }
                dragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                active = null
                dragging = false
                return handledByDelegate
            }
            else -> return active != null || handledByDelegate
        }
    }

    /** The button with some extra room around it, easier to hit. */
    private fun onButton(span: TableSpan, x: Float, y: Float): Boolean {
        val slack = span.dp(8)
        val button = span.toggleRect
        return x >= button.left - slack &&
            x <= button.right + slack &&
            y >= button.top - slack &&
            y <= button.bottom + slack
    }

    /** The table under a touch, remembering where it is drawn. */
    private fun findTable(textView: TextView, touchX: Float, touchY: Float): TableSpan? {
        val layout = textView.layout ?: return null
        val spanned = textView.text as? Spanned ?: return null
        val y = touchY - textView.totalPaddingTop + textView.scrollY
        val line = layout.getLineForVertical(y.toInt())
        val spans =
            spanned.getSpans(
                layout.getLineStart(line),
                layout.getLineEnd(line),
                TableSpan::class.java,
            )
        for (span in spans) {
            val start = spanned.getSpanStart(span)
            if (layout.getLineForOffset(start) != line) continue
            tableLeft =
                layout.getPrimaryHorizontal(start) + textView.totalPaddingLeft - textView.scrollX
            tableTop =
                (layout.getLineBaseline(line) + textView.totalPaddingTop -
                        textView.scrollY -
                        span.height)
                    .toFloat()
            val x = touchX - tableLeft
            val localY = touchY - tableTop
            if (x >= 0 && localY >= 0 && localY <= span.height) return span
        }
        return null
    }

    companion object {
        private const val TAG_KEY = 0x7E7AB1E5

        /**
         * Wraps the view's touch listener, [delegate], which keeps being called for every touch.
         * Text views without a listener of their own pass null.
         */
        fun install(view: View?, delegate: View.OnTouchListener?) {
            if (view == null || view.getTag(TAG_KEY) != null) return
            view.setTag(TAG_KEY, true)
            view.setOnTouchListener(TableTouch(view, delegate))
        }
    }
}
