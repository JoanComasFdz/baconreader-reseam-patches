// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.embedcommentimages

import android.content.Context
import android.widget.FrameLayout

/** A FrameLayout whose height follows its width (height = width * ratio), at most [maxHeight]. */
internal class AspectRatioFrameLayout(context: Context, ratio: Float, private val maxHeight: Int) :
    FrameLayout(context) {
    var ratio: Float = ratio
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = Math.min(Math.round(width * ratio), maxHeight)
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }

    override fun shouldDelayChildPressedState(): Boolean = false
}
