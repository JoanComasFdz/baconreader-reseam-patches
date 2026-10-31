// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.embedcommentimages

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.joancomasfdz.baconreader.common.DownloadProgress
import com.joancomasfdz.baconreader.common.ErrorDialog
import com.joancomasfdz.baconreader.common.GlideUtils
import com.joancomasfdz.baconreader.embedcommentimages.CommentImages.dp
import com.joancomasfdz.baconreader.embedcommentimages.CommentImages.withAlpha
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.onelouder.baconreader.imgur_gallery.ImagesRetriever
import com.onelouder.baconreader.imgur_gallery.ImgurImage
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.Executors
import java.util.regex.Pattern

/**
 * Imgur albums (imgur.com/a/... and imgur.com/gallery/... links) in comments and posts. The images
 * are listed with the app's own Imgur client, and shown one at a time in the comment: swipe
 * sideways to change the image. Each image behaves like any other embedded image.
 */
internal object ImgurAlbum {
    private const val TAG = "ImgurAlbum"

    private val ALBUM =
        Pattern.compile(
            "^https?://(?:www\\.|m\\.)?imgur\\.com/(a|gallery)/(?:[^/?#]*-)?([A-Za-z0-9]+)/?(?:[?#].*)?$",
            Pattern.CASE_INSENSITIVE,
        )

    // ImagesRetriever's request types.
    private const val REQUEST_GALLERY = 0
    private const val REQUEST_ALBUM = 1

    // Album link to its images.
    private val albums = LruCache<String, List<ImageRef>>(20)

    // The image shown in each album, to keep it when the comment is shown again.
    private val positions = LruCache<String, Int>(50)

    private val layouts = WeakHashMap<View, AlbumLayout>()
    private val loader = Executors.newFixedThreadPool(2)

    // ImagesRetriever is an AsyncTask, whose doInBackground is protected. Calling it directly lists
    // the images on this thread, without the task's callback.
    private val listImages by lazy {
        ImagesRetriever::class
            .java
            .getDeclaredMethod("doInBackground", Array<Void>::class.java)
            .apply { isAccessible = true }
    }

    /** Whether the link is an Imgur album or gallery. */
    fun isAlbum(url: String?): Boolean = url != null && ALBUM.matcher(url).matches()

    /**
     * A slot for the album: a placeholder with a progress bar while the album is listed, then its
     * images, one at a time.
     */
    fun createSlot(textView: TextView, url: String): View {
        val context = textView.context
        val slot = AlbumLayout(textView, url)
        slot.layoutParams =
            CommentImages.slotLayoutParams(textView, ViewGroup.LayoutParams.WRAP_CONTENT)
        layouts[slot] = slot

        val known = albums.get(url)
        if (known != null) {
            slot.show(known)
            return slot
        }

        slot.showLoading()
        loader.execute {
            val images = fetch(url)
            if (!images.isNullOrEmpty()) albums.put(url, images)
            slot.post {
                // The view shows another comment now.
                if (slot.released) return@post
                if (images.isNullOrEmpty()) slot.showUnavailable() else slot.show(images)
            }
        }
        return slot
    }

    /** The view is reused for another comment: drops the image the album shows. */
    fun release(slot: View) {
        val layout = layouts.remove(slot) ?: return
        layout.released = true
        layout.discardPage()
    }

    /** The images of an album, or null when they could not be listed. Blocks. */
    private fun fetch(url: String): List<ImageRef>? {
        val matcher = ALBUM.matcher(url)
        if (!matcher.matches()) return null
        val requestType =
            if (matcher.group(1).equals("a", ignoreCase = true)) REQUEST_ALBUM else REQUEST_GALLERY
        val id = matcher.group(2)
        for (attempt in 1..GlideUtils.MAX_ATTEMPTS) {
            try {
                val retriever = ImagesRetriever(id, requestType, null)
                val items = listImages.invoke(retriever, emptyArray<Void>()) as List<*>?
                if (items != null) return items.mapNotNull { toImage(it as? ImgurImage) }
            } catch (exception: Exception) {
                Log.i(TAG, "Could not list the Imgur album $url", exception)
            }
            try {
                Thread.sleep(GlideUtils.RETRY_DELAY_MS * attempt)
            } catch (exception: InterruptedException) {
                return null
            }
        }
        return null
    }

    /**
     * An album image. Images use a medium size while small and the original when expanded; GIFs are
     * shown as GIFs; videos, which can't be played here, show a still.
     */
    private fun toImage(item: ImgurImage?): ImageRef? {
        val id = item?.id ?: return null
        val link = item.link ?: return null
        val mp4 = item.mp4
        val path = link.lowercase(Locale.ROOT).replaceFirst(Regex("[?#].*$"), "")
        if (path.endsWith(".gif")) return ImageRef(link, link, 0, 0)
        if (!mp4.isNullOrEmpty() || path.endsWith(".mp4")) {
            val still = "https://i.imgur.com/${id}h.jpg"
            return ImageRef(still, still, 0, 0)
        }
        return ImageRef("https://i.imgur.com/${id}l.jpg", link, 0, 0)
    }

    /**
     * Shows one image of the album, and changes it when swiped sideways. The swipe is taken from
     * the list only once it is clearly sideways, so the list still scrolls.
     */
    @SuppressLint("ViewConstructor")
    private class AlbumLayout(private val textView: TextView, private val url: String) :
        FrameLayout(textView.context) {
        private val counter = TextView(context)
        private val touchSlop: Int
        private val minFlingVelocity: Int
        var released = false

        private var images: List<ImageRef>? = null
        private var position = 0
        private var page: View? = null

        private var downX = 0f
        private var downY = 0f
        private var dragging = false
        private var velocity: VelocityTracker? = null

        init {
            val configuration = ViewConfiguration.get(context)
            touchSlop = configuration.scaledTouchSlop
            minFlingVelocity = configuration.scaledMinimumFlingVelocity * 4

            counter.setTextColor(Color.WHITE)
            counter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            counter.background =
                GradientDrawable().apply {
                    setColor(0x99000000.toInt())
                    cornerRadius = dp(context, 10).toFloat()
                }
            counter.setPadding(dp(context, 8), dp(context, 2), dp(context, 8), dp(context, 2))
            counter.visibility = GONE
            addView(
                counter,
                LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP or Gravity.END,
                    )
                    .apply { setMargins(0, dp(context, 6), dp(context, 6), 0) },
            )
        }

        /** A box with a moving progress bar while the album is listed. */
        fun showLoading() {
            val box = box()
            val progress = DownloadProgress(context)
            box.addView(progress.root, bottomParams())
            progress.start()
            setPage(box)
        }

        fun showUnavailable() {
            val box = box()
            val message = TextView(context)
            message.text = BaconReaderStrings.embedImageUnavailable
            message.setTextColor(withAlpha(textView.currentTextColor, 0x80))
            message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            box.addView(
                message,
                LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
            val progress = DownloadProgress(context)
            box.addView(progress.root, bottomParams())
            progress.fail()
            setPage(box)
        }

        fun show(images: List<ImageRef>) {
            this.images = images
            val saved = positions.get(url)
            showPage(if (saved != null && saved < images.size) saved else 0)
        }

        private fun bottomParams() =
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            )

        private fun showPage(position: Int) {
            val images = images ?: return
            val previous =
                if (page != null && this.position < images.size) images[this.position] else null
            this.position = position
            positions.put(url, position)
            val image = images[position]
            // Swiping keeps the size the user chose for the album.
            if (previous != null && previous !== image) CommentImages.inheritSize(previous, image)
            try {
                setPage(CommentImages.createImageSlot(textView, image))
            } catch (exception: Exception) {
                ErrorDialog.report(TAG, "Could not show the album image", exception)
            }
            counter.text = "${position + 1} / ${images.size}"
            counter.visibility = if (images.size > 1) VISIBLE else GONE
            counter.bringToFront()
        }

        private fun setPage(view: View) {
            discardPage()
            page = view
            addView(view, 0)
            // The album slot has the margins of an image slot already.
            (view.layoutParams as? MarginLayoutParams)?.apply {
                topMargin = 0
                leftMargin = 0
                rightMargin = 0
            }
        }

        fun discardPage() {
            val current = page ?: return
            CommentImages.discardSlot(current)
            removeView(current)
            page = null
        }

        /** A placeholder box of the size of a small image, in the colors of the text. */
        private fun box(): FrameLayout {
            val box = FrameLayout(context)
            box.background =
                GradientDrawable().apply {
                    setColor(withAlpha(textView.currentTextColor, 0x1A))
                    cornerRadius = dp(context, 6).toFloat()
                }
            val size = dp(context, CommentImages.IMAGE_SIZE_DP)
            box.layoutParams =
                LayoutParams(size, Math.max(textView.lineHeight * 2, dp(context, 32)))
            return box
        }

        /**
         * The album wraps its image, so a full width image would only get its own size: the album
         * takes the full width while its image does.
         */
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            var widthSpec = widthMeasureSpec
            if (
                page?.layoutParams?.width == ViewGroup.LayoutParams.MATCH_PARENT &&
                    MeasureSpec.getMode(widthSpec) == MeasureSpec.AT_MOST
            ) {
                widthSpec =
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthSpec), MeasureSpec.EXACTLY)
            }
            super.onMeasure(widthSpec, heightMeasureSpec)
        }

        private fun canSwipe(): Boolean = (images?.size ?: 0) > 1 && page != null

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            if (!canSwipe()) return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    dragging = false
                    trackVelocity(event, true)
                }
                MotionEvent.ACTION_MOVE -> {
                    trackVelocity(event, false)
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                        dragging = true
                        parent.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> recycleVelocity()
            }
            return false
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!canSwipe()) return super.onTouchEvent(event)
            val current = page ?: return true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    trackVelocity(event, true)
                }
                MotionEvent.ACTION_MOVE -> {
                    trackVelocity(event, false)
                    val dx = event.x - downX
                    if (!dragging && Math.abs(dx) > touchSlop) {
                        dragging = true
                        parent.requestDisallowInterceptTouchEvent(true)
                    }
                    if (dragging) current.translationX = resisted(dx)
                }
                MotionEvent.ACTION_UP -> {
                    trackVelocity(event, false)
                    val moved = event.x - downX
                    var speed = 0f
                    velocity?.let {
                        it.computeCurrentVelocity(1000)
                        speed = it.xVelocity
                    }
                    recycleVelocity()
                    if (dragging) settle(moved, speed)
                    dragging = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    recycleVelocity()
                    if (dragging) settle(0f, 0f)
                    dragging = false
                }
            }
            return true
        }

        /** The first and last image move less, to show there is nothing past them. */
        private fun resisted(dx: Float): Float {
            val last = (images?.size ?: 1) - 1
            val pastEnd = (dx > 0 && position == 0) || (dx < 0 && position == last)
            return if (pastEnd) dx / 3 else dx
        }

        /** Goes to the next or previous image when swiped far or fast enough, else snaps back. */
        private fun settle(dx: Float, speed: Float) {
            val current = page ?: return
            val count = images?.size ?: return
            val width = Math.max(current.width, 1)
            var target = position
            if (dx < -width / 4f || speed < -minFlingVelocity) target = position + 1
            else if (dx > width / 4f || speed > minFlingVelocity) target = position - 1
            if (target < 0 || target >= count) {
                current.animate().translationX(0f).setDuration(150).start()
                return
            }
            val next = target
            val out = if (next > position) -width.toFloat() else width.toFloat()
            current
                .animate()
                .translationX(out)
                .setDuration(150)
                .withEndAction {
                    if (released || page !== current) return@withEndAction
                    showPage(next)
                    page?.let {
                        it.translationX = -out
                        it.animate().translationX(0f).setDuration(150).start()
                    }
                }
                .start()
        }

        private fun trackVelocity(event: MotionEvent, reset: Boolean) {
            if (reset || velocity == null) {
                recycleVelocity()
                velocity = VelocityTracker.obtain()
            }
            velocity?.addMovement(event)
        }

        private fun recycleVelocity() {
            velocity?.recycle()
            velocity = null
        }
    }
}
