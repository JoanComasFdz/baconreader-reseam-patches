// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.slideshow

import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The text of an image, GIF or video post in the Slide Show View. It sits under the media in a page
 * of fixed height, and BaconReader lets it grow past the page's bottom when it is expanded: what
 * doesn't fit is cut off. It goes into a scroll view instead, inside a vertical column, where
 * "Embed comment images" puts the text's media.
 */
internal object SlideShowText {
    private const val PAGE = "com.onelouder.baconreader.adapters.PortraitPageLayout"

    fun makeScrollable(text: TextView) {
        if (text.parent?.parent is EdgeScrollView || !inSlideShow(text)) return
        val parent = text.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(text)
        val margins = text.layoutParams as? ViewGroup.MarginLayoutParams
        parent.removeViewAt(index)

        val context = text.context
        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL
        column.addView(
            text,
            LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                .apply {
                    if (margins != null) {
                        setMargins(
                            margins.leftMargin,
                            margins.topMargin,
                            margins.rightMargin,
                            margins.bottomMargin,
                        )
                    }
                },
        )
        val scroll = EdgeScrollView(context)
        scroll.addView(
            column,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        // In the page's column, under the media, which takes the height the text leaves it.
        parent.addView(
            scroll,
            index,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private fun inSlideShow(text: TextView): Boolean {
        var view = text.parent
        while (view != null) {
            if (view.javaClass.name == PAGE) return true
            view = view.parent
        }
        return false
    }

    /**
     * Scrolls its text while it can, and leaves the swipe to the page's list at its ends, and to
     * the post pager when it goes sideways.
     */
    class EdgeScrollView(context: Context) : ScrollView(context) {
        private var startX = 0f
        private var startY = 0f
        private var lastY = 0f

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                // The list and the pager saw the touch start; a quick flick would be theirs on its
                // first move, before the text moves.
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    lastY = event.y
                    parent?.requestDisallowInterceptTouchEvent(
                        canScrollVertically(1) || canScrollVertically(-1)
                    )
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.y - lastY
                    lastY = event.y
                    val vertical = Math.abs(event.y - startY) > Math.abs(event.x - startX)
                    if (!vertical) {
                        parent?.requestDisallowInterceptTouchEvent(false)
                    } else if (dy != 0f) {
                        parent?.requestDisallowInterceptTouchEvent(
                            canScrollVertically(if (dy < 0) 1 else -1)
                        )
                    }
                }
            }
            return super.dispatchTouchEvent(event)
        }
    }
}
