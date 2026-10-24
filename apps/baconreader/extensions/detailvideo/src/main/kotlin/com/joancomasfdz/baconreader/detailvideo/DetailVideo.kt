// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("DetailVideo")

package com.joancomasfdz.baconreader.detailvideo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ViewFlipper
import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.common.ErrorDialog
import com.joancomasfdz.baconreader.common.GlideUtils
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.joancomasfdz.baconreader.settings.DetailVideoSettings
import com.onelouder.baconreader.reddit.Link
import java.util.WeakHashMap

/*
 * With "Autoplay videos in Detail View" off, the detail view of a post shows the post's preview
 * image with a play button in place of the video, which isn't loaded. Tapping it hands the video
 * back to the app, which starts it as it would have. PostMediaHandler is also used by the
 * full-screen photo viewer and the browser, which keep autoplaying.
 */

private const val TAG = "DetailVideo"

// The listener DetailLinkFragment gives its PostMediaHandler. PhotoSlide and BrowserActivity give
// their own.
private const val DETAIL_LISTENER = "com.onelouder.baconreader.adapters.DetailLinkFragment\$6"

/** The preview page each PostMediaHandler has in its ViewFlipper while it holds a video. */
private val pages = WeakHashMap<Any, PreviewPage>()

/** Set while the app shows the video the user tapped, so the hook lets it through. */
private var showing = false

/**
 * Whether the app's display of a video is skipped for a preview, at the start of
 * PostMediaHandler.displayVideo.
 */
@Export
fun deferVideo(
    listener: Any?,
    handler: Any?,
    link: Link?,
    flipper: ViewFlipper?,
    videoHandler: Any?,
    context: Context?,
    url: String?,
): Boolean {
    if (showing || handler == null || flipper == null || context == null) return false
    if (videoHandler == null || listener?.javaClass?.name != DETAIL_LISTENER) return false
    if (DetailVideoSettings.autoplayVideosInDetailView.value) return false
    try {
        pages.remove(handler)?.let { flipper.removeView(it) }
        val page = PreviewPage(context, previewUrl(link))
        page.setOnClickListener { play(handler, flipper, page, context, url) }
        pages[handler] = page
        flipper.addView(page)
        // The app shows the flipper with its first page (DetailLinkFragment).
        flipper.visibility = View.VISIBLE
        flipper.displayedChild = flipper.indexOfChild(page)
        return true
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not show the video preview", exception)
        return false
    }
}

/** A static image of the post: the largest preview resolution, else the source. */
private fun previewUrl(link: Link?): String? {
    val image = link?.preview?.images?.firstOrNull() ?: return null
    val best = image.resolutions?.lastOrNull() ?: image.source
    return best?.url?.replace("&amp;", "&")
}

private fun play(
    handler: Any,
    flipper: ViewFlipper,
    page: PreviewPage,
    context: Context,
    url: String?,
) {
    pages.remove(handler)
    showing = true
    try {
        handler.javaClass
            .getDeclaredMethod("displayVideo", Context::class.java, String::class.java)
            .apply { isAccessible = true }
            .invoke(handler, context, url)
        flipper.removeView(page)
        GlideUtils.clear(page.image)
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not play the video", exception)
    } finally {
        showing = false
    }
}

private fun dp(context: Context, value: Int): Int =
    (value * context.resources.displayMetrics.density).toInt()

/** The post's preview image with a play button, as a page of the media's ViewFlipper. */
private class PreviewPage(context: Context, previewUrl: String?) : FrameLayout(context) {
    val image = ImageView(context)

    init {
        setBackgroundColor(0xFF000000.toInt())
        minimumHeight = dp(context, 200)
        contentDescription = BaconReaderStrings.detailVideoPlay
        image.adjustViewBounds = true
        image.scaleType = ImageView.ScaleType.FIT_CENTER
        addView(
            image,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        val size = dp(context, 64)
        addView(PlayButton(context), LayoutParams(size, size, Gravity.CENTER))
        if (previewUrl != null) GlideUtils.load(image, previewUrl, null, null)
    }
}

private class PlayButton(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val triangle = Path()

    override fun onDraw(canvas: Canvas) {
        val radius = minOf(width, height) / 2f
        val x = width / 2f
        val y = height / 2f
        paint.color = 0xB3000000.toInt()
        canvas.drawCircle(x, y, radius, paint)
        triangle.reset()
        triangle.moveTo(x - radius * 0.25f, y - radius * 0.4f)
        triangle.lineTo(x - radius * 0.25f, y + radius * 0.4f)
        triangle.lineTo(x + radius * 0.45f, y)
        triangle.close()
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawPath(triangle, paint)
    }
}
