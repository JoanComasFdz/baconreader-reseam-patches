// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.common

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.util.Locale
import java.util.WeakHashMap

/**
 * A progress bar with the downloaded size ("1.2 / 3.4 MB"), like the one of post media. Without a
 * known size, the bar stays animated and no size is shown. When loading fails, the bar stops and
 * turns red. Add [root] to the layout; it starts hidden.
 */
class DownloadProgress(context: Context) {
    val root: LinearLayout = LinearLayout(context)
    private val label = TextView(context)
    private val bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
    private val accent = accentColor(context)

    init {
        root.orientation = LinearLayout.VERTICAL
        root.visibility = View.GONE

        label.setTextColor(Color.WHITE)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        val horizontal = dp(context, 8)
        val vertical = dp(context, 3)
        label.setPadding(horizontal, vertical, horizontal, vertical)
        label.background =
            GradientDrawable().apply {
                setColor(0x99000000.toInt())
                cornerRadius = dp(context, 10).toFloat()
            }
        label.visibility = View.GONE
        root.addView(
            label,
            LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                .apply { setMargins(dp(context, 6), 0, 0, dp(context, 4)) },
        )

        bar.max = MAX
        bar.progressTintList = ColorStateList.valueOf(accent)
        bar.indeterminateTintList = ColorStateList.valueOf(accent)
        bar.progressBackgroundTintList = ColorStateList.valueOf(0x33FFFFFF)
        root.addView(
            bar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 4)),
        )
    }

    fun start() {
        showLoading(bar, accent)
        label.visibility = View.GONE
        root.visibility = View.VISIBLE
    }

    /** [total] is -1 or 0 when the server does not tell the size. */
    fun update(received: Long, total: Long) {
        // Unknown size: the bar stays animated.
        if (total <= 0) return
        bar.isIndeterminate = false
        bar.progress = (MAX.toLong() * received / total).toInt()
        label.text = progressLabel(received, total)
        label.visibility = View.VISIBLE
    }

    fun stop() {
        root.visibility = View.GONE
    }

    /** A full red bar, without animation: the media could not be loaded. */
    fun fail() {
        showFailed(bar)
        label.visibility = View.GONE
        root.visibility = View.VISIBLE
    }

    companion object {
        /** The color of a bar whose media could not be loaded. */
        const val FAILED_COLOR = 0xFFE53935.toInt()

        private const val MAX = 1000

        // The progress drawable of bars shown as failed, to restore it on the next load.
        private val originalDrawables = WeakHashMap<ProgressBar, Drawable>()

        /**
         * Stops the animation of a horizontal bar and shows it as a static red line, as thin as the
         * moving one: the indeterminate drawable draws a line half the height of the bar, centered.
         */
        fun showFailed(bar: ProgressBar) {
            if (!originalDrawables.containsKey(bar)) originalDrawables[bar] = bar.progressDrawable
            val height = bar.layoutParams?.height?.takeIf { it > 0 } ?: dp(bar.context, 4)
            val inset = height / 4
            bar.isIndeterminate = false
            bar.progressTintList = null
            bar.progressDrawable = InsetDrawable(ColorDrawable(FAILED_COLOR), 0, inset, 0, inset)
            bar.progress = bar.max
        }

        /** Shows a bar as loading again, animated in [accent], after [showFailed]. */
        fun showLoading(bar: ProgressBar, accent: Int) {
            originalDrawables.remove(bar)?.let { bar.progressDrawable = it }
            bar.progressTintList = ColorStateList.valueOf(accent)
            bar.isIndeterminate = true
        }

        /** "1.2 / 3.4 MB". */
        fun progressLabel(received: Long, total: Long): String {
            val mb = 1024.0 * 1024
            return String.format(Locale.US, "%.1f / %.1f MB", received / mb, total / mb)
        }

        private fun accentColor(context: Context): Int {
            val array = context.theme.obtainStyledAttributes(intArrayOf(android.R.attr.colorAccent))
            try {
                return array.getColor(0, 0xFF4F7EC4.toInt())
            } finally {
                array.recycle()
            }
        }

        private fun dp(context: Context, value: Int): Int =
            Math.round(
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    value.toFloat(),
                    context.resources.displayMetrics,
                )
            )
    }
}
