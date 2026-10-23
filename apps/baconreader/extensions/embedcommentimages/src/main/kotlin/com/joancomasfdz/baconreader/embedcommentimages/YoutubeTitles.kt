// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.embedcommentimages

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.text.style.URLSpan
import android.util.Log
import android.widget.TextView
import com.joancomasfdz.baconreader.common.GlideUtils
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Collections
import java.util.Locale
import java.util.concurrent.Executors
import java.util.regex.Pattern
import org.json.JSONObject

/**
 * Bare YouTube links (the link text is the URL itself) show a small spinner, then the video's title
 * from YouTube's oEmbed endpoint. The link keeps its spans, so it stays clickable and keeps the
 * app's "red YouTube links" color. Links written as [text](url) are left alone.
 */
internal object YoutubeTitles {
    private const val TAG = "YoutubeTitles"
    private const val SPINNER_SEPARATOR = " "
    private const val CACHE_SIZE = 300
    private const val FRAME_MS = 50L
    private const val THUMBNAIL_WIDTH_DP = 200
    private val HANDLE = Pattern.compile("/@([^/?#]+)")
    private val VIDEO_ID =
        Pattern.compile(
            "(?:youtu\\.be/|/(?:shorts|live|embed|v)/)([A-Za-z0-9_-]{11})|[?&]v=([A-Za-z0-9_-]{11})"
        )

    // A channel by its @handle, or one of the channel's pages (videos, shorts...).
    private val CHANNEL =
        Pattern.compile(
            "^[a-z-]+://[^/]+/(@[^/?#]+)(?:/(?:featured|videos|shorts|streams|playlists|community|about))?/?(?:[?#].*)?$",
            Pattern.CASE_INSENSITIVE,
        )
    private val PLAYLIST =
        Pattern.compile(
            "^[a-z-]+://[^/]+/playlist\\?(?:[^#]*&)?list=([A-Za-z0-9_-]+)",
            Pattern.CASE_INSENSITIVE,
        )

    // A 4:3 thumbnail with black bars, as oEmbed gives them; the same image exists in 16:9.
    private val LETTERBOXED_THUMBNAIL =
        Pattern.compile("^(https://i\\.ytimg\\.com/vi/[^/]+/)hqdefault\\.jpg$")
    private val START_PARAM = Pattern.compile("[?&#](?:t|start)=([0-9hms]+)")
    private val TIME_PART = Pattern.compile("(\\d+)([hms])")

    // URL to "@channel - title", or "" when YouTube has no title for it (removed, private, not a
    // video).
    private val titles = lruMap()

    // URL to the thumbnail from oEmbed, for links whose thumbnail doesn't follow from the URL
    // (playlists).
    private val thumbnails = lruMap()

    private val loader = Executors.newFixedThreadPool(2)

    private fun lruMap(): MutableMap<String, String> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, String>(64, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, String>?
                ): Boolean = size > CACHE_SIZE
            }
        )

    /** A link waiting for its title. */
    class Pending(val spinner: ImageSpan, val link: URLSpan, val url: String)

    fun isYoutubeUrl(url: String?): Boolean {
        if (url == null) return false
        val host =
            url.lowercase(Locale.ROOT)
                .replaceFirst(Regex("^[a-z-]+://"), "")
                .replaceFirst(Regex("[/?#:].*$"), "")
        return host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
    }

    /**
     * The text shows the URL itself (Reddit auto-links, or [url](url)), not words the author
     * linked.
     */
    fun isBareYoutubeLink(linkText: String?, url: String?): Boolean {
        if (!isYoutubeUrl(CommentImages.normalizeLink(url)) || linkText == null) return false
        val text = linkText.trim()
        return text.isNotEmpty() &&
            !text.matches(Regex(".*\\s.*")) &&
            text.lowercase(Locale.ROOT).contains("youtu")
    }

    private fun oembedUrl(url: String): String =
        "https://www.youtube.com/oembed?format=json&url=" + URLEncoder.encode(url, "UTF-8")

    /**
     * Applied to the new text before it is set: known titles replace their link text right away,
     * the others get a spinner after the link.
     */
    fun prepare(
        textView: TextView,
        builder: SpannableStringBuilder,
        links: List<URLSpan>,
    ): List<Pending> {
        val pending = ArrayList<Pending>()
        if (links.isEmpty()) return pending
        val size = Math.round(textView.textSize * 0.85f)
        for (link in fromTheEnd(builder, links)) {
            val start = builder.getSpanStart(link)
            val end = builder.getSpanEnd(link)
            if (start < 0 || end <= start) continue
            val url = CommentImages.normalizeLink(link.url) ?: continue
            // YouTube has no title for channels; the link has their @handle.
            val channel = channelLabel(url)
            if (channel != null) {
                replaceKeepingSpans(builder, start, end, channel)
                continue
            }
            val title = titles[url]
            if (title != null) {
                if (title.isNotEmpty())
                    replaceKeepingSpans(builder, start, end, title + suffix(url))
                continue
            }
            val spinner =
                ImageSpan(SpinnerDrawable(textView.currentTextColor, size), ImageSpan.ALIGN_BOTTOM)
            insertAfterKeepingSpans(builder, start, end, SPINNER_SEPARATOR + "￼")
            val spinnerStart = end + SPINNER_SEPARATOR.length
            builder.setSpan(
                spinner,
                spinnerStart,
                spinnerStart + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            pending.add(Pending(spinner, link, url))
        }
        return pending
    }

    /** The links from the last to the first, so the offsets of earlier ones stay valid. */
    fun fromTheEnd(builder: SpannableStringBuilder, links: List<URLSpan>): List<URLSpan> =
        links.sortedByDescending {
            builder.getSpanStart(it)
        }

    /** Looks the titles up in the background and swaps them in while the view shows this text. */
    fun load(textView: TextView, generation: Any, pending: List<Pending>) {
        if (pending.isEmpty()) return
        animate(textView, generation) { text -> pending.any { text.getSpanStart(it.spinner) >= 0 } }
        for (p in pending) {
            loader.execute {
                val title = fetchTitle(p.url)
                textView.post { apply(textView, generation, p, title) }
            }
        }
    }

    private fun apply(textView: TextView, generation: Any, p: Pending, title: String?) {
        try {
            if (!CommentImages.isCurrent(textView, generation)) return
            val current = CommentImages.fullText(textView) as? Spanned ?: return
            if (current.getSpanStart(p.spinner) < 0) return

            val builder = SpannableStringBuilder(current)
            val start = builder.getSpanStart(p.link)
            val end = builder.getSpanEnd(p.link)
            if (!title.isNullOrEmpty() && start >= 0 && end > start) {
                replaceKeepingSpans(builder, start, end, title + suffix(p.url))
            }
            val spinnerStart = builder.getSpanStart(p.spinner)
            builder.removeSpan(p.spinner)
            val from = spinnerStart - SPINNER_SEPARATOR.length
            if (
                from >= 0 && builder.subSequence(from, spinnerStart).toString() == SPINNER_SEPARATOR
            ) {
                builder.delete(from, spinnerStart + 1)
            } else {
                builder.delete(spinnerStart, spinnerStart + 1)
            }
            textView.setText(builder, TextView.BufferType.SPANNABLE)
        } catch (exception: Exception) {
            Log.i(TAG, "Could not show the title", exception)
        }
    }

    /**
     * Redraws the spinners while [spinning] says one is left in the text, and the view still shows
     * the content they were added for.
     */
    fun animate(textView: TextView, generation: Any, spinning: (Spanned) -> Boolean) {
        textView.postDelayed(
            object : Runnable {
                override fun run() {
                    if (!CommentImages.isCurrent(textView, generation)) return
                    val text = CommentImages.fullText(textView) as? Spanned ?: return
                    if (!spinning(text)) return
                    textView.invalidate()
                    textView.postDelayed(this, FRAME_MS)
                }
            },
            FRAME_MS,
        )
    }

    /**
     * The video title, "" when YouTube has none for this URL, or null after network errors, to try
     * again next time.
     */
    private fun fetchTitle(url: String): String? {
        var attempt = 1
        while (true) {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(oembedUrl(url)).openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                val code = connection.responseCode
                if (code in 400..499) {
                    // Removed, private or not a video: asking again won't change it.
                    titles[url] = ""
                    return ""
                }
                if (code != 200) throw IOException("HTTP $code")
                val body = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                val json = JSONObject(body)
                val title =
                    label(
                        json.optString("title", ""),
                        json.optString("author_name", ""),
                        json.optString("author_url", ""),
                    )
                val thumbnail = json.optString("thumbnail_url", "")
                if (thumbnail.startsWith("https://")) thumbnails[url] = sixteenByNine(thumbnail)
                titles[url] = title
                return title
            } catch (exception: Exception) {
                if (attempt >= GlideUtils.MAX_ATTEMPTS) {
                    Log.i(TAG, "Could not look up the title of $url", exception)
                    return null
                }
                try {
                    Thread.sleep(GlideUtils.RETRY_DELAY_MS * attempt)
                } catch (interrupted: InterruptedException) {
                    return null
                }
            } finally {
                connection?.disconnect()
            }
            attempt++
        }
    }

    /** The 11-character video id of a watch, youtu.be, shorts, live or embed link, or null. */
    private fun videoId(url: String?): String? {
        if (url == null || !isYoutubeUrl(url)) return null
        val matcher = VIDEO_ID.matcher(url)
        return if (matcher.find()) matcher.group(1) ?: matcher.group(2) else null
    }

    /** "@handle [Channel]" for a link to a channel, or null. */
    private fun channelLabel(url: String): String? {
        if (!isYoutubeUrl(url)) return null
        val matcher = CHANNEL.matcher(url)
        if (!matcher.matches()) return null
        var handle = matcher.group(1)!!
        try {
            handle = URLDecoder.decode(handle, "UTF-8")
        } catch (exception: Exception) {
            // Shown as it is in the link.
        }
        return handle + " " + BaconReaderStrings.youtubeTitleChannel
    }

    /** Whether the link is a playlist page (youtube.com/playlist?list=...), not a video of one. */
    private fun isPlaylist(url: String?): Boolean =
        url != null && isYoutubeUrl(url) && PLAYLIST.matcher(url).find()

    /** Whether the link gets a thumbnail: a video, or a playlist (its first video's). */
    fun hasThumbnail(url: String?): Boolean = videoId(url) != null || isPlaylist(url)

    private fun sixteenByNine(thumbnailUrl: String): String {
        val matcher = LETTERBOXED_THUMBNAIL.matcher(thumbnailUrl)
        return if (matcher.matches()) matcher.group(1) + "mqdefault.jpg" else thumbnailUrl
    }

    // 320x180, 16:9 without black bars.
    private fun thumbnailUrl(videoId: String): String =
        "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"

    private fun isShort(url: String?): Boolean =
        url != null && url.lowercase(Locale.ROOT).matches(Regex("^[a-z-]+://[^/]+/shorts/.*"))

    /** The start time from t= or start= (83, 83s, 1m23s, 1h2m3s), in seconds, or -1. */
    private fun startSeconds(url: String?): Int {
        if (url == null) return -1
        val matcher = START_PARAM.matcher(url)
        if (!matcher.find()) return -1
        val value = matcher.group(1)!!.lowercase(Locale.ROOT)
        if (value.matches(Regex("\\d+"))) return value.toIntOrNull() ?: -1
        val parts = TIME_PART.matcher(value)
        var seconds = 0
        var any = false
        while (parts.find()) {
            val n = parts.group(1)!!.toInt()
            seconds +=
                when (parts.group(2)) {
                    "h" -> n * 3600
                    "m" -> n * 60
                    else -> n
                }
            any = true
        }
        return if (any) seconds else -1
    }

    fun formatTime(seconds: Int): String {
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    /** " (from 1:23)", " [Short]" and " [Playlist]", added after the title. */
    private fun suffix(url: String): String = buildString {
        val start = startSeconds(url)
        if (start > 0)
            append(' ').append(BaconReaderStrings.youtubeTitleFromTime(formatTime(start)))
        if (isShort(url)) append(' ').append(BaconReaderStrings.youtubeTitleShort)
        if (isPlaylist(url)) append(' ').append(BaconReaderStrings.youtubeTitlePlaylist)
    }

    /** "@channel - Title": the channel's @handle, or its name when it has no handle. */
    private fun label(title: String?, authorName: String?, authorUrl: String?): String {
        val trimmed = title?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        val handle = HANDLE.matcher(authorUrl ?: "")
        val channel =
            if (handle.find()) {
                try {
                    "@" + URLDecoder.decode(handle.group(1), "UTF-8")
                } catch (exception: Exception) {
                    "@" + handle.group(1)
                }
            } else if (!authorName.isNullOrBlank()) {
                authorName.trim()
            } else {
                null
            }
        return if (channel == null) trimmed else "$channel - $trimmed"
    }

    /**
     * Replaces [start, end) and moves the spans that covered exactly it (link, color) onto the new
     * text.
     */
    private fun replaceKeepingSpans(
        builder: SpannableStringBuilder,
        start: Int,
        end: Int,
        replacement: CharSequence,
    ) {
        val spans = exactSpans(builder, start, end)
        for (span in spans) builder.removeSpan(span)
        builder.replace(start, end, replacement)
        for (span in spans) {
            builder.setSpan(
                span,
                start,
                start + replacement.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    /** Inserts text right after [start, end) without the link's spans growing over it. */
    fun insertAfterKeepingSpans(
        builder: SpannableStringBuilder,
        start: Int,
        end: Int,
        text: CharSequence,
    ) {
        val spans = exactSpans(builder, start, end)
        for (span in spans) builder.removeSpan(span)
        builder.insert(end, text)
        for (span in spans) builder.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun exactSpans(builder: SpannableStringBuilder, start: Int, end: Int): List<Any> =
        builder.getSpans(start, end, Any::class.java).filter {
            builder.getSpanStart(it) == start && builder.getSpanEnd(it) == end
        }

    /** A small rotating arc, drawn in the text color. */
    class SpinnerDrawable(color: Int, private val size: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val oval = RectF()

        init {
            paint.color = (color and 0x00FFFFFF) or 0xB0000000.toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = Math.max(2f, size / 8f)
            setBounds(0, 0, size, size)
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val inset = paint.strokeWidth
            oval.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset)
            val angle = (SystemClock.uptimeMillis() % 1000) * 360f / 1000f
            canvas.drawArc(oval, angle, 270f, false, paint)
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
    }

    /** A thumbnail drawn in the text, on its own line under the line with its link. */
    class Thumbnail(
        val placeholder: ImageSpan,
        // The thumbnail, or null when it has to be looked up from the link (playlists).
        val imageUrl: String?,
        val linkUrl: String,
        val width: Int,
        val height: Int,
        val fullWidth: Boolean,
    )

    /**
     * Adds a line with a 16:9 placeholder after the line of each link. The placeholder has the
     * link's URLSpan too, so tapping the thumbnail opens the video like tapping the link does.
     */
    fun insertThumbnails(
        textView: TextView,
        builder: SpannableStringBuilder,
        links: List<URLSpan>,
        fullWidth: Boolean,
    ): List<Thumbnail> {
        val result = ArrayList<Thumbnail>()
        if (links.isEmpty()) return result
        val width = if (fullWidth) contentWidth(textView) else dp(textView, THUMBNAIL_WIDTH_DP)
        val height = Math.round(width * 9f / 16f)
        for (link in fromTheEnd(builder, links)) {
            val end = builder.getSpanEnd(link)
            if (end < 0) continue
            val linkUrl = CommentImages.normalizeLink(link.url) ?: continue
            val id = videoId(linkUrl)
            if (id == null && !isPlaylist(linkUrl)) continue
            var lineEnd = builder.indexOf('\n', end)
            if (lineEnd < 0) lineEnd = builder.length
            builder.insert(lineEnd, "\n￼")
            val at = lineEnd + 1

            val box = GradientDrawable()
            box.setColor((textView.currentTextColor and 0x00FFFFFF) or 0x1A000000)
            box.cornerRadius = dp(textView, 6).toFloat()
            box.setBounds(0, 0, width, height)
            val placeholder = ImageSpan(box, ImageSpan.ALIGN_BOTTOM)
            builder.setSpan(placeholder, at, at + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(URLSpan(link.url), at, at + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            result.add(
                Thumbnail(
                    placeholder,
                    id?.let { thumbnailUrl(it) },
                    linkUrl,
                    width,
                    height,
                    fullWidth,
                )
            )
        }
        return result
    }

    fun loadThumbnails(textView: TextView, generation: Any, thumbnails: List<Thumbnail>) {
        for (thumbnail in thumbnails) {
            loader.execute {
                try {
                    var imageUrl = thumbnail.imageUrl
                    if (imageUrl == null) {
                        // Playlists: the thumbnail comes with the title.
                        imageUrl = this.thumbnails[thumbnail.linkUrl]
                        if (imageUrl == null && !titles.containsKey(thumbnail.linkUrl)) {
                            fetchTitle(thumbnail.linkUrl)
                            imageUrl = this.thumbnails[thumbnail.linkUrl]
                        }
                        if (imageUrl == null) return@execute
                    }
                    val image =
                        GlideUtils.fetch(textView, imageUrl, thumbnail.width, thumbnail.height)
                    textView.post { showThumbnail(textView, generation, thumbnail, image) }
                } catch (exception: Exception) {
                    Log.i(TAG, "Could not load the YouTube thumbnail", exception)
                }
            }
        }
    }

    private fun showThumbnail(
        textView: TextView,
        generation: Any,
        thumbnail: Thumbnail,
        image: Drawable,
    ) {
        if (!CommentImages.isCurrent(textView, generation)) return
        val text = CommentImages.fullText(textView) as? Spannable ?: return
        val start = text.getSpanStart(thumbnail.placeholder)
        val end = text.getSpanEnd(thumbnail.placeholder)
        if (start < 0) return
        var width = thumbnail.width
        var height = thumbnail.height
        if (thumbnail.fullWidth) {
            // The first guess was made before the view had a size; the real width is known now.
            val measured = textView.width - textView.totalPaddingLeft - textView.totalPaddingRight
            if (measured > 0) {
                width = measured
                height = Math.round(width * 9f / 16f)
            }
        }
        val drawable = PlayOverlay(image, PlayDrawable(dp(textView, 44)))
        drawable.setBounds(0, 0, width, height)
        text.removeSpan(thumbnail.placeholder)
        text.setSpan(
            ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        CommentImages.fullTextChanged(textView, text)
    }

    private fun contentWidth(textView: TextView): Int {
        val width = textView.width - textView.totalPaddingLeft - textView.totalPaddingRight
        if (width > 0) return width
        return textView.resources.displayMetrics.widthPixels - dp(textView, 32)
    }

    fun dp(view: TextView, value: Int): Int =
        Math.round(value * view.resources.displayMetrics.density)

    /** A thumbnail or video still with the play button in its center. */
    class PlayOverlay(private val image: Drawable, private val play: Drawable) : Drawable() {
        override fun draw(canvas: Canvas) {
            val b = bounds
            image.bounds = b
            image.draw(canvas)
            val size = Math.min(play.intrinsicWidth, Math.min(b.width(), b.height()) / 2)
            val left = b.centerX() - size / 2
            val top = b.centerY() - size / 2
            play.setBounds(left, top, left + size, top + size)
            play.draw(canvas)
        }

        override fun setAlpha(alpha: Int) {
            image.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            image.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    /** A white play triangle in a dark circle. */
    class PlayDrawable(private val size: Int) : Drawable() {
        private val circle = Paint(Paint.ANTI_ALIAS_FLAG)
        private val triangle = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()

        init {
            circle.color = 0x99000000.toInt()
            triangle.color = 0xFFFFFFFF.toInt()
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = Math.min(b.width(), b.height()) / 2f
            canvas.drawCircle(cx, cy, r, circle)
            val t = r * 0.45f
            path.reset()
            path.moveTo(cx - t * 0.7f, cy - t)
            path.lineTo(cx - t * 0.7f, cy + t)
            path.lineTo(cx + t, cy)
            path.close()
            canvas.drawPath(path, triangle)
        }

        override fun getIntrinsicWidth(): Int = size

        override fun getIntrinsicHeight(): Int = size

        override fun setAlpha(alpha: Int) {
            circle.alpha = alpha
            triangle.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            triangle.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
