// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.textposticon

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.max
import kotlin.math.min

/**
 * The expand button of text posts in the list. BaconReader draws it as a triangle in a circle,
 * which looks like a video that did not load. This is a page with three text lines instead, and a
 * chevron to its right: down when the text is closed, up when it is open.
 */
internal class PageDrawable(color: Int, private val size: Int, density: Float) : Drawable() {
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = max(1.5f * density, size / 16f)
        }
    private val page = RectF()
    private val path = Path()

    /** 0 is closed, the chevron pointing down; 1 is open, pointing up. */
    private var progress = 0f
    private var animator: ValueAnimator? = null

    fun setExpanded(expanded: Boolean, animate: Boolean) {
        val target = if (expanded) 1f else 0f
        animator?.cancel()
        if (!animate || progress == target) {
            progress = target
            invalidateSelf()
            return
        }
        animator =
            ValueAnimator.ofFloat(progress, target).apply {
                duration = ANIMATION_MS
                addUpdateListener {
                    progress = it.animatedValue as Float
                    invalidateSelf()
                }
                start()
            }
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()).toFloat()
        val left = b.exactCenterX() - s / 2f
        val top = b.exactCenterY() - s / 2f

        // The page on the left, vertically centered.
        val pageWidth = s * 0.42f
        val pageHeight = s * 0.56f
        val pageLeft = left + s * 0.06f
        val pageTop = top + (s - pageHeight) / 2f
        page.set(pageLeft, pageTop, pageLeft + pageWidth, pageTop + pageHeight)
        val corner = s * 0.06f
        canvas.drawRoundRect(page, corner, corner, paint)

        // Text lines, the last one shorter.
        val lineLeft = pageLeft + pageWidth * 0.22f
        val lineRight = pageLeft + pageWidth * 0.78f
        for (i in 0 until 3) {
            val y = pageTop + pageHeight * (0.3f + 0.2f * i)
            val right = if (i == 2) lineLeft + (lineRight - lineLeft) * 0.6f else lineRight
            canvas.drawLine(lineLeft, y, right, y, paint)
        }

        // The chevron on the right, with a gap after the page.
        val cx = left + s * 0.8f
        val cy = b.exactCenterY()
        val half = s * 0.12f
        val depth = s * 0.06f * (1f - 2f * progress)
        path.reset()
        path.moveTo(cx - half, cy - depth)
        path.lineTo(cx, cy + depth)
        path.lineTo(cx + half, cy - depth)
        canvas.drawPath(path, paint)
    }

    override fun getIntrinsicWidth(): Int = size

    override fun getIntrinsicHeight(): Int = size

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val ANIMATION_MS = 200L
    }
}
