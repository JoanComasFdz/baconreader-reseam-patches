// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("RedditGallery")

package com.joancomasfdz.baconreader.postmedia

import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ViewFlipper
import app.reseam.runtime.Export
import com.github.chrisbanes.photoview.PhotoView
import com.joancomasfdz.baconreader.common.DownloadProgress
import com.joancomasfdz.baconreader.common.ErrorDialog
import com.joancomasfdz.baconreader.common.GlideUtils
import com.joancomasfdz.baconreader.common.ImageDownloader
import com.onelouder.baconreader.reddit_gallery.RedditImage
import com.onelouder.baconreader.reddit_gallery.RedditImagePreview
import java.io.File
import java.util.WeakHashMap

/*
 * BaconReader turns the small previews of a gallery's thumbnail strip into the full images
 * ("preview.redd.it" to "i.redd.it", as the previews' "&amp;" are not unescaped), so every
 * thumbnail downloads a full image, often a big GIF, and the image shown waits for them. The strip
 * uses the previews instead, and the image shown is downloaded with a progress bar, with its
 * preview shown until it is loaded.
 */

private const val GALLERY_TAG = "RedditGallery"

/** Smallest preview width used for the thumbnail strip, which shows them at about 100dp. */
private const val MIN_PREVIEW_WIDTH = 216

/** RedditGalleryViewer's page of the image. */
private const val PAGE_IMAGE = 1

/** The progress bar of each gallery viewer. */
private val progressBars = WeakHashMap<Any, DownloadProgress>()

/** The image each gallery viewer shows now, so a late download of another one is dropped. */
private val shown = WeakHashMap<Any, String>()

/**
 * Which of an image's previews, smallest first, the gallery uses, instead of the first, which is
 * too small for the thumbnail strip.
 */
@Export
fun previewIndex(previews: List<RedditImagePreview?>?): Int {
    var index = 0
    try {
        for ((i, preview) in previews.orEmpty().withIndex()) {
            index = i
            val width = preview?.x?.toIntOrNull()
            if (width != null && width >= MIN_PREVIEW_WIDTH) break
        }
    } catch (exception: Exception) {
        Log.i(GALLERY_TAG, "Could not choose the gallery preview", exception)
    }
    return index
}

/**
 * The thumbnail of a gallery image: its preview, with its "&amp;" unescaped. Null lets the app
 * decide.
 */
@Export
fun thumbnail(image: RedditImage?): String? {
    val preview = image?.imagePreview
    if (preview == null || !preview.startsWith("http")) return null
    return preview.replace("&amp;", "&")
}

/**
 * Loads the image shown in RedditGalleryViewer.glide(RedditImage). Returns the URL it loads, which
 * the app saves on a long press, or null to let the app load it.
 */
@Export
fun loadImage(
    viewer: Any?,
    image: RedditImage?,
    imageView: PhotoView?,
    flipper: ViewFlipper?,
    doubleTap: GestureDetector.OnDoubleTapListener?,
): String? {
    if (viewer == null || imageView == null || flipper == null) return null
    try {
        val url = image?.link
        if (url == null || !url.startsWith("http")) return null

        val context = imageView.context
        val preview = thumbnail(image)
        val progress = progress(viewer, flipper)
        shown[viewer] = url

        if (ImageDownloader.cached(context, url) == null) {
            // The preview, with the progress bar, until the image is downloaded.
            progress.start()
            imageView.setImageDrawable(null)
            flipper.displayedChild = PAGE_IMAGE
            if (preview != null) GlideUtils.load(imageView, preview, null, null)
        } else {
            // The red bar of an image that could not be loaded.
            progress.stop()
        }

        ImageDownloader.download(
            context,
            url,
            object : ImageDownloader.Listener {
                override fun onProgress(received: Long, total: Long) {
                    if (url == shown[viewer]) progress.update(received, total)
                }

                override fun onDownloaded(file: File) {
                    if (url != shown[viewer]) return
                    progress.stop()
                    show(viewer, imageView, flipper, doubleTap, file, url, preview)
                }

                override fun onFailed() {
                    if (url != shown[viewer]) return
                    progress.stop()
                    show(viewer, imageView, flipper, doubleTap, null, url, preview)
                }
            },
        )
        return url
    } catch (exception: Exception) {
        ErrorDialog.report(GALLERY_TAG, "Could not load the gallery image", exception)
        return null
    }
}

/** Shows the downloaded image, or lets Glide load it when the download failed. */
private fun show(
    viewer: Any,
    imageView: PhotoView,
    flipper: ViewFlipper,
    doubleTap: GestureDetector.OnDoubleTapListener?,
    file: File?,
    url: String,
    preview: String?,
) {
    val callback =
        object : GlideUtils.LoadCallback {
            override fun onReady() {
                // As the app's own listener: its double tap listener, and the image replaces the
                // spinner.
                imageView.setOnDoubleTapListener(doubleTap)
                flipper.displayedChild = PAGE_IMAGE
            }

            override fun onFailed() {
                flipper.displayedChild = PAGE_IMAGE
                progressBars[viewer]?.fail()
            }
        }
    try {
        if (file != null) {
            GlideUtils.load(imageView, file, preview, callback)
        } else {
            GlideUtils.load(imageView, url, preview, callback)
        }
    } catch (exception: Exception) {
        ErrorDialog.report(GALLERY_TAG, "Could not show the gallery image", exception)
    }
}

/** The progress bar of the viewer, at the bottom of the image. */
private fun progress(viewer: Any, flipper: ViewFlipper): DownloadProgress =
    progressBars.getOrPut(viewer) {
        DownloadProgress(flipper.context).also {
            (flipper.parent as ViewGroup).addView(
                it.root,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM,
                ),
            )
        }
    }
