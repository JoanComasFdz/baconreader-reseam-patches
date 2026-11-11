// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("SwipeThroughAlbums")

package com.joancomasfdz.baconreader.albums

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import app.reseam.runtime.Export
import com.github.chrisbanes.photoview.OnSingleFlingListener
import com.github.chrisbanes.photoview.PhotoView
import com.joancomasfdz.baconreader.common.ErrorDialog
import kotlin.math.abs

private const val TAG = "SwipeThroughAlbums"

/** Shortest swipe, in dp, that changes image: a tap that slides a little doesn't. */
private const val MIN_DISTANCE_DP = 48

/** Longest swipe on a video, in milliseconds: a slower drag is no swipe. */
private const val MAX_SWIPE_MS = 1000L

/**
 * Called at the end of RedditGalleryViewer.createView and ImgurGalleryViewer.createView, with the
 * viewer's image and arrows: a horizontal swipe on the image taps the arrow on its side, left for
 * the next image, right for the previous. PhotoView reports the swipe only with one finger while
 * the image isn't zoomed in, so a zoomed image still pans, and a pinch is never a swipe.
 */
@Export
fun attachToImage(image: PhotoView?, back: View?, forward: View?) {
    try {
        if (image == null || back == null || forward == null) return
        val density = image.resources.displayMetrics.density
        image.setOnSingleFlingListener(
            OnSingleFlingListener { down, up, _, _ -> swipe(down, up, back, forward, density) }
        )
    } catch (t: Throwable) {
        ErrorDialog.report(TAG, "Could not let the album swipe", t)
    }
}

/**
 * Called at the end of ImgurGalleryViewer.createView, with the album's video player and arrows: the
 * video plays in its own view, whose surface pauses and plays it on a tap. A swipe on it goes to
 * the next or previous item, and any other touch reaches the surface as before.
 */
@Export
@SuppressLint("ClickableViewAccessibility")
fun attachToVideo(player: View?, back: View?, forward: View?) {
    try {
        if (player == null || back == null || forward == null) return
        val surface =
            player.javaClass.getMethod("getVideoSurfaceView").invoke(player) as View? ?: return
        val density = surface.resources.displayMetrics.density
        var down: MotionEvent? = null
        // Android's GestureDetector never reports a fling on the surface: the swipe is read from
        // where the finger landed and lifted, and when.
        surface.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    down?.recycle()
                    down = MotionEvent.obtain(event)
                    false
                }
                MotionEvent.ACTION_UP -> {
                    val start = down
                    if (
                        start == null ||
                            event.eventTime - start.eventTime > MAX_SWIPE_MS ||
                            !swipe(start, event, back, forward, density)
                    ) {
                        false
                    } else {
                        // The swipe isn't a tap: the surface gets a cancel instead of the lift,
                        // so it doesn't pause the video.
                        val cancel = MotionEvent.obtain(event)
                        cancel.action = MotionEvent.ACTION_CANCEL
                        view.onTouchEvent(cancel)
                        cancel.recycle()
                        true
                    }
                }
                else -> false
            }
        }
    } catch (t: Throwable) {
        ErrorDialog.report(TAG, "Could not let the album's video swipe", t)
    }
}

// A horizontal swipe long enough taps the arrow on its side, when the viewer shows it: none before
// the first image, none after the last.
private fun swipe(
    down: MotionEvent?,
    up: MotionEvent?,
    back: View,
    forward: View,
    density: Float,
): Boolean {
    if (down == null || up == null) return false
    val dx = up.x - down.x
    val dy = up.y - down.y
    if (abs(dx) < MIN_DISTANCE_DP * density || abs(dx) < abs(dy) * 2) return false
    val arrow = if (dx < 0) forward else back
    return arrow.isShown && arrow.performClick()
}
