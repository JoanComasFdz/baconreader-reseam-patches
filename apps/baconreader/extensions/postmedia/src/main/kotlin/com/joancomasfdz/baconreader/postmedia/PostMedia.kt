// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("PostMedia")

package com.joancomasfdz.baconreader.postmedia

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.webkit.WebSettings
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ViewFlipper
import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.common.DownloadProgress
import com.joancomasfdz.baconreader.common.ErrorDialog
import com.joancomasfdz.baconreader.common.GlideUtils
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.joancomasfdz.baconreader.settings.PostMediaSettings
import com.onelouder.baconreader.media.VideoHandler
import com.onelouder.baconreader.reddit.Link
import com.onelouder.baconreader.reddit.RedditPreviewImage
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.Executors
import retrofit2.Response

/*
 * While post media loads, an overlay on the media area shows a dimmed still preview, a progress bar
 * and, once a size is known, a label ("GIF · 80.6 MB", or "12.3 / 80.6 MB"). GIFs load through the
 * app's own cache loader, which reports the download progress.
 *
 * The overlay is drawn in the ViewOverlay of the media's ViewFlipper, so it floats above whichever
 * page the app shows (an empty PhotoView while a GIF downloads) without changing the app's views.
 */

private const val TAG = "PostMedia"

private const val PAGE_LOADING = 0
private const val PAGE_VIDEO = 3

// ExoPlayer's Player.STATE_READY.
private const val PLAYER_READY = 3

private const val HTTP_NOT_FOUND = 404
private const val HTTP_GONE = 410

/** One overlay per PostMediaHandler. */
private val overlays = WeakHashMap<Any, LoadingOverlay>()

/** The HTTP status of the RedGIFs API error being reported on this thread. */
private val redgifsError = ThreadLocal<Int>()

private val sizeLookup = Executors.newFixedThreadPool(2)

/** Shows the loading overlay, at the start of PostMediaHandler.start(). */
@Export
fun onStart(
    handler: Any?,
    link: Link?,
    flipper: ViewFlipper?,
    videoHandler: VideoHandler?,
) {
    if (handler == null || flipper == null) return
    try {
        val overlay =
            overlays.getOrPut(handler) { LoadingOverlay(flipper, WeakReference(videoHandler)) }
        overlay.show(stillPreviewUrl(link))

        val gif = gifUrl(link)
        // Reddit videos (DASH) have no single file size: the bar alone shows they are loading.
        if (gif != null && link?.isRedditVideo != true) overlay.lookUpSize("GIF", gif)
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not show the loading overlay", exception)
    }
}

/**
 * Whether PostMediaHandler.loadGifContent should load the URL with the app's own cache loader
 * instead of Glide: true for GIFs. The cache loader streams the file to the app's image cache and
 * reports the progress, then shows it with the app's GifImageView. Static images keep Glide.
 */
@Export fun loadsWithProgress(url: String?): Boolean = isGifUrl(url)

/**
 * Shows the loading bar as failed where the app gives up loading an image or GIF: the OkHttp
 * download that follows a failed Glide load. The app only logs it and keeps the loading page.
 */
@Export
fun onImageLoadFailed(handler: Any?) {
    val overlay = handler?.let { overlays[it] } ?: return
    overlay.flipper.post { overlay.fail() }
}

/** The download progress of the app's cache loader. */
@Export
fun onProgress(handler: Any?, received: Int, total: Int) {
    if (handler == null) return
    val update = Runnable { overlays[handler]?.setProgress(received, total) }
    if (Looper.myLooper() == Looper.getMainLooper()) {
        update.run()
    } else {
        overlays[handler]?.flipper?.post(update)
    }
}

/** Remembers the error of the RedGIFs API, right before BaconReader cancels the request. */
@Export
fun onRedgifsError(response: Response<*>?) {
    if (response == null) return
    redgifsError.set(response.code())
}

/**
 * The message to show when BaconReader cancels loading a RedGIFs video, or null to let the app
 * continue. For a deleted or missing video, the app would show RedGIFs' web page, which doesn't
 * work without JavaScript.
 */
@Export
fun redgifsErrorMessage(): String? {
    val code = redgifsError.get() ?: return null
    redgifsError.remove()
    return when (code) {
        HTTP_GONE -> BaconReaderStrings.postMediaRedgifsDeleted
        HTTP_NOT_FOUND -> BaconReaderStrings.postMediaRedgifsNotFound
        else -> null
    }
}

/** Whether the URL's path ends in .gif: i.redd.it/x.gif, preview.redd.it/x.gif?s=... */
private fun isGifUrl(url: String?): Boolean {
    if (url == null) return false
    return unescape(url)!!
        .lowercase(Locale.ROOT)
        .replaceFirst(Regex("[?#].*$"), "")
        .endsWith(".gif")
}

/** The GIF the app downloads for GIF posts: link.preview.images[0].variants.gif.source.url. */
private fun gifUrl(link: Link?): String? =
    unescape(firstPreviewImage(link)?.variants?.gif?.source?.url)

/** A static image of the post: the largest preview resolution, else the source. */
private fun stillPreviewUrl(link: Link?): String? {
    val image = firstPreviewImage(link) ?: return null
    val best = image.resolutions?.lastOrNull() ?: image.source
    return unescape(best?.url)
}

private fun firstPreviewImage(link: Link?): RedditPreviewImage? =
    link?.preview?.images?.firstOrNull()

/**
 * The still preview, label and progress bar, drawn above the media's ViewFlipper. It stays until
 * the page the app shows has content: an image in the photo or GIF view, a playing video, or any
 * other page (web, blocked, error).
 */
private class LoadingOverlay(
    val flipper: ViewFlipper,
    private val videoHandler: WeakReference<VideoHandler?>,
) {
    private val root = FrameLayout(flipper.context)
    private val still = ImageView(flipper.context)
    private val label = TextView(flipper.context)
    private val progress =
        ProgressBar(flipper.context, null, android.R.attr.progressBarStyleHorizontal)
    private val accent = accentColor(flipper.context)
    private var attached = false
    private var progressKnown = false
    private var failed = false

    /** Changes on every [show], so a late size lookup for a previous post is ignored. */
    private var generation = Any()

    private val watcher = ViewTreeObserver.OnPreDrawListener {
        if (attached && contentShown()) hide()
        true
    }

    /**
     * Video frames on a SurfaceView don't trigger pre-draw, so this also checks twice a second. A
     * video that fails (no connection, removed) stays on the video page: the bar then stops and
     * turns red. The checks go on, so the overlay still hides if the video plays later.
     */
    private val ticker =
        object : Runnable {
            override fun run() {
                if (!attached) return
                if (contentShown()) {
                    hide()
                    return
                }
                if (playerFailed() || offline()) fail()
                flipper.postDelayed(this, TICK_MS)
            }
        }

    private val resizer = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> layout() }

    init {
        val context = flipper.context
        still.scaleType = ImageView.ScaleType.FIT_CENTER
        still.alpha = 0.45f
        root.addView(
            still,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        val bottom = LinearLayout(context)
        bottom.orientation = LinearLayout.VERTICAL
        label.setTextColor(0xFFFFFFFF.toInt())
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        val horizontal = dp(context, 8)
        val vertical = dp(context, 3)
        label.setPadding(horizontal, vertical, horizontal, vertical)
        label.background =
            GradientDrawable().apply {
                setColor(0x99000000.toInt())
                cornerRadius = dp(context, 10).toFloat()
            }
        bottom.addView(
            label,
            LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                .apply { setMargins(dp(context, 8), 0, 0, dp(context, 6)) },
        )

        progress.max = MAX
        progress.progressTintList = ColorStateList.valueOf(accent)
        progress.indeterminateTintList = ColorStateList.valueOf(accent)
        progress.progressBackgroundTintList = ColorStateList.valueOf(0x33FFFFFF)
        bottom.addView(
            progress,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 4)),
        )
        root.addView(
            bottom,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ),
        )
    }

    fun show(stillUrl: String?) {
        generation = Any()
        progressKnown = false
        failed = false
        DownloadProgress.showLoading(progress, accent)
        // The bar shows it is loading; the label only appears with a size.
        setLabel(null)

        GlideUtils.clear(still)
        still.setImageDrawable(null)
        if (!attached) {
            flipper.overlay.add(root)
            flipper.addOnLayoutChangeListener(resizer)
            flipper.viewTreeObserver.addOnPreDrawListener(watcher)
            attached = true
        }
        layout()
        flipper.removeCallbacks(ticker)
        flipper.postDelayed(ticker, TICK_MS)
        if (stillUrl != null) {
            try {
                GlideUtils.load(still, stillUrl, null, null)
            } catch (exception: Exception) {
                Log.i(TAG, "Could not load the still preview", exception)
            }
        }
    }

    private fun hide() {
        if (!attached) return
        attached = false
        flipper.removeCallbacks(ticker)
        flipper.overlay.remove(root)
        flipper.removeOnLayoutChangeListener(resizer)
        if (flipper.viewTreeObserver.isAlive) {
            flipper.viewTreeObserver.removeOnPreDrawListener(watcher)
        }
        GlideUtils.clear(still)
    }

    /** The real progress, for media the app loads through its own cache loader. */
    fun setProgress(received: Int, total: Int) {
        if (!attached || failed) return
        if (total > 0) {
            progressKnown = true
            progress.isIndeterminate = false
            progress.progress = (MAX.toLong() * received / total).toInt()
        }
        setLabel(progressLabel(received.toLong(), total.toLong()))
    }

    /** Shows the size label, or hides it when there is nothing to show. */
    private fun setLabel(text: String?) {
        label.text = text
        label.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        // Views in a ViewOverlay are not measured again when their text changes.
        layout()
    }

    /** Shows "GIF · 80.6 MB" while loading, with the size the server tells for a HEAD request. */
    fun lookUpSize(kind: String, url: String) {
        val requested = generation
        sizeLookup.execute {
            val size = contentLength(url)
            if (size <= 0) return@execute
            flipper.post {
                if (attached && generation === requested && !progressKnown && !failed) {
                    setLabel(sizeLabel(kind, size))
                }
            }
        }
    }

    private fun contentShown(): Boolean {
        if (flipper.visibility != View.VISIBLE) return false
        val current = flipper.currentView
        return when (flipper.displayedChild) {
            PAGE_LOADING -> false
            PAGE_VIDEO -> playerReady()
            // Web page, blocked, error, galleries: nothing to wait for.
            else -> current !is ImageView || current.drawable != null
        }
    }

    /** Whether the app's video player has a frame ready, for videos the app starts itself. */
    private fun playerReady(): Boolean =
        try {
            val player = videoHandler.get()?.player
            player != null && player.playbackState == PLAYER_READY
        } catch (throwable: Throwable) {
            // Never leave the overlay stuck on top of a video.
            true
        }

    /** The media could not be loaded: the bar stops and turns red. */
    fun fail() {
        if (!attached || failed) return
        failed = true
        DownloadProgress.showFailed(progress)
        setLabel(null)
    }

    /** Without a connection, the app's loaders give up without telling: link resolving, images. */
    @Suppress("DEPRECATION")
    private fun offline(): Boolean =
        try {
            val connectivity =
                flipper.context.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as ConnectivityManager?
            val network = connectivity?.activeNetworkInfo
            network == null || !network.isConnected
        } catch (throwable: Throwable) {
            false
        }

    /** Whether the app's video player stopped with an error. */
    private fun playerFailed(): Boolean =
        try {
            flipper.displayedChild == PAGE_VIDEO && videoHandler.get()?.player?.playerError != null
        } catch (throwable: Throwable) {
            false
        }

    private fun layout() {
        val width = flipper.width
        val height = flipper.height
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, width, height)
    }

    companion object {
        private const val MAX = 1000
        private const val TICK_MS = 500L
    }
}

/** "12.3 / 84.5 MB", or "12.3 MB" when the total size is unknown. */
private fun progressLabel(received: Long, total: Long): String =
    if (total > 0) {
        DownloadProgress.progressLabel(received, total)
    } else {
        String.format(Locale.US, "%.1f MB", received / (1024.0 * 1024))
    }

/** "GIF · 80.6 MB". */
private fun sizeLabel(kind: String, bytes: Long): String =
    String.format(Locale.US, "%s · %.1f MB", kind, bytes / (1024.0 * 1024))

/** The size the server tells for a URL, or -1. Blocks. */
private fun contentLength(url: String): Long {
    var connection: HttpURLConnection? = null
    return try {
        connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "HEAD"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        if (connection.responseCode == 200) connection.contentLength.toLong() else -1
    } catch (exception: Exception) {
        -1
    } finally {
        connection?.disconnect()
    }
}

private fun accentColor(context: Context): Int {
    val array = context.theme.obtainStyledAttributes(intArrayOf(android.R.attr.colorAccent))
    try {
        return array.getColor(0, 0xFF4F7EC4.toInt())
    } finally {
        array.recycle()
    }
}

private fun unescape(url: String?): String? = url?.replace("&amp;", "&")

private fun dp(context: Context, value: Int): Int =
    Math.round(
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics,
        )
    )

/**
 * Turns JavaScript on in the web view of a link post when the settings say the page runs it: all
 * sites except the blocked ones, or only the allowed ones. Replaces the app's own list of sites,
 * after the app set its default.
 */
@Export
fun applyJavascript(settings: WebSettings?, url: String?) {
    if (settings != null && needsJavascript(url)) settings.javaScriptEnabled = true
}

private fun needsJavascript(url: String?): Boolean {
    try {
        val uri = Uri.parse(url ?: return false)
        val scheme = uri.scheme
        // Like the app: anything but a web page, such as an app link, runs JavaScript.
        if (
            !"http".equals(scheme, ignoreCase = true) && !"https".equals(scheme, ignoreCase = true)
        ) {
            return true
        }
        val host = uri.host ?: return false
        if (PostMediaSettings.linkPreviewJavascript.value) {
            return !isOnSites(host, PostMediaSettings.linkPreviewJavascriptBlocked.value)
        }
        return isOnSites(host, PostMediaSettings.linkPreviewJavascriptAllowed.value)
    } catch (exception: Exception) {
        Log.i(TAG, "Could not check the site of $url", exception)
        return false
    }
}

/**
 * Whether the host is one of the sites, or a subdomain of one. Sites are separated by lines, spaces
 * or commas, and can be written as links: "https://www.example.com/page" is example.com.
 */
private fun isOnSites(host: String, sites: String): Boolean {
    val lowerHost = host.lowercase(Locale.ROOT)
    for (entry in sites.split(Regex("[\\s,]+"))) {
        val site =
            entry
                .trim()
                .lowercase(Locale.ROOT)
                .replaceFirst(Regex("^[a-z]+://"), "")
                .replaceFirst(Regex("[/?#:].*$"), "")
                .replaceFirst(Regex("^www\\."), "")
        if (site.isEmpty()) continue
        if (lowerHost == site || lowerHost.endsWith(".$site")) return true
    }
    return false
}
