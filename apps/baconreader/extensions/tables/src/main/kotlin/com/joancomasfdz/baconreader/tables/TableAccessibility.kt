// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.tables

import android.graphics.Rect
import android.os.Bundle
import android.text.Spanned
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import android.widget.TextView

/**
 * Shows the tables of a TextView to accessibility. A table is drawn on one character of the text,
 * so screen readers and UI tests would see that character and nothing of its cells or its button:
 * each cell and the expand or fit button are virtual views of the TextView, with their text and
 * where they show, and the button and the cells with a link can be clicked.
 */
internal class TableAccessibility private constructor(private val view: TextView) :
    View.AccessibilityDelegate() {
    private val provider =
        object : AccessibilityNodeProvider() {
            override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? =
                if (virtualViewId == HOST_VIEW_ID) host() else part(virtualViewId)

            override fun performAction(
                virtualViewId: Int,
                action: Int,
                arguments: Bundle?,
            ): Boolean {
                if (virtualViewId == HOST_VIEW_ID) {
                    return view.performAccessibilityAction(action, arguments)
                }
                if (action != AccessibilityNodeInfo.ACTION_CLICK) return false
                val (span, part) = find(virtualViewId) ?: return false
                when {
                    part.isButton -> {
                        span.toggle()
                        TableSpan.relayout(view)
                    }
                    part.link != null -> part.link.onClick(view)
                    else -> return false
                }
                view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED)
                return true
            }
        }

    override fun getAccessibilityNodeProvider(host: View): AccessibilityNodeProvider = provider

    /** The TextView itself, as it describes itself, with the tables' parts as its children. */
    private fun host(): AccessibilityNodeInfo {
        val info = AccessibilityNodeInfo.obtain(view)
        view.onInitializeAccessibilityNodeInfo(info)
        tables().forEachIndexed { table, (span, _) ->
            span.parts().forEachIndexed { index, part ->
                if (!part.bounds.isEmpty) info.addChild(view, id(table, index))
            }
        }
        return info
    }

    private fun part(virtualViewId: Int): AccessibilityNodeInfo? {
        val (span, part) = find(virtualViewId) ?: return null
        val origin = origin(span) ?: return null
        val info = AccessibilityNodeInfo.obtain(view, virtualViewId)
        info.setParent(view)
        info.packageName = view.context.packageName
        info.className = if (part.isButton) "android.widget.Button" else "android.widget.TextView"
        // Named as the patches name the views they make, so UI tests find them.
        info.viewIdResourceName =
            "${view.context.packageName}:id/baconreader_patches_table_" +
                if (part.isButton) "button" else "cell"
        info.text = part.text
        info.isEnabled = true
        val bounds = Rect()
        part.bounds.roundOut(bounds)
        bounds.offset(origin[0], origin[1])
        val shown = Rect()
        val visible = view.getGlobalVisibleRect(shown) && bounds.intersect(shown)
        info.setBoundsInScreen(if (visible) bounds else Rect())
        info.isVisibleToUser = visible
        if (part.isButton || part.link != null) {
            info.isClickable = true
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
        }
        return info
    }

    /** The table's top left on the screen, or null when its line isn't laid out. */
    private fun origin(span: TableSpan): IntArray? {
        val layout = view.layout ?: return null
        val text = view.text as? Spanned ?: return null
        val start = text.getSpanStart(span)
        if (start < 0) return null
        val line = layout.getLineForOffset(start)
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        location[0] +=
            (layout.getPrimaryHorizontal(start) + view.totalPaddingLeft - view.scrollX).toInt()
        location[1] +=
            layout.getLineBaseline(line) + view.totalPaddingTop - view.scrollY - span.height
        return location
    }

    private fun tables(): List<Pair<TableSpan, Int>> {
        val text = view.text as? Spanned ?: return emptyList()
        return text
            .getSpans(0, text.length, TableSpan::class.java)
            .map { it to text.getSpanStart(it) }
            .sortedBy { it.second }
    }

    private fun find(virtualViewId: Int): Pair<TableSpan, TablePart>? {
        val span = tables().getOrNull(virtualViewId / PARTS)?.first ?: return null
        val part = span.parts().getOrNull(virtualViewId % PARTS) ?: return null
        return span to part
    }

    private fun id(table: Int, part: Int) = table * PARTS + part

    companion object {
        /** Virtual view ids per table: its cells and its button. */
        private const val PARTS = 100_000

        private const val TAG_KEY = 0x7E7AB1E6

        /** Shows the tables of [view] to accessibility, once per view. */
        fun install(view: TextView) {
            if (view.getTag(TAG_KEY) != null) return
            view.setTag(TAG_KEY, true)
            view.accessibilityDelegate = TableAccessibility(view)
        }
    }
}
