// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.embedcommentimages

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView

/** An ImageView with pinch to zoom, drag to pan and double tap to zoom in and out. */
@SuppressLint("AppCompatCustomView")
internal class ZoomImageView(context: Context) : ImageView(context) {
    private val transform = Matrix()
    private var zoom = 1f
    var onTap: Runnable? = null
    var onLongPress: Runnable? = null

    private val scaleDetector =
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    zoomBy(detector.scaleFactor, detector.focusX, detector.focusY)
                    return true
                }
            },
        )

    private val gestureDetector =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onScroll(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    dx: Float,
                    dy: Float,
                ): Boolean {
                    if (zoom <= 1.01f) return false
                    transform.postTranslate(-dx, -dy)
                    constrain()
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (zoom > 1.01f) fit() else zoomBy(DOUBLE_TAP_ZOOM, e.x, e.y)
                    return true
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    onTap?.run()
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    onLongPress?.run()
                }
            },
        )

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        fit()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fit()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    /** Fits the image to the view, centered. */
    private fun fit() {
        val drawable = drawable ?: return
        val vw = width
        val vh = height
        if (vw == 0 || vh == 0) return
        val dw = drawable.intrinsicWidth
        val dh = drawable.intrinsicHeight
        if (dw <= 0 || dh <= 0) return
        val scale = Math.min(vw.toFloat() / dw, vh.toFloat() / dh)
        transform.setScale(scale, scale)
        transform.postTranslate((vw - dw * scale) / 2f, (vh - dh * scale) / 2f)
        zoom = 1f
        setImageMatrix(transform)
    }

    private fun zoomBy(factor: Float, focusX: Float, focusY: Float) {
        val target = Math.max(1f, Math.min(MAX_ZOOM, zoom * factor))
        val applied = target / zoom
        zoom = target
        transform.postScale(applied, applied, focusX, focusY)
        constrain()
    }

    /** Keeps the image on screen: centered when smaller than the view, its edges clamped else. */
    private fun constrain() {
        val drawable = drawable ?: return
        val rect =
            RectF(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        transform.mapRect(rect)
        var dx = 0f
        var dy = 0f
        val vw = width
        val vh = height
        if (rect.width() <= vw) dx = (vw - rect.width()) / 2f - rect.left
        else if (rect.left > 0) dx = -rect.left else if (rect.right < vw) dx = vw - rect.right
        if (rect.height() <= vh) dy = (vh - rect.height()) / 2f - rect.top
        else if (rect.top > 0) dy = -rect.top else if (rect.bottom < vh) dy = vh - rect.bottom
        transform.postTranslate(dx, dy)
        setImageMatrix(transform)
    }

    private companion object {
        const val MAX_ZOOM = 5f
        const val DOUBLE_TAP_ZOOM = 2.5f
    }
}
