// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.embedcommentimages

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.widget.TextView
import com.joancomasfdz.baconreader.common.GlideUtils
import com.joancomasfdz.baconreader.embedcommentimages.YoutubeTitles.dp
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.regex.Pattern
import org.json.JSONObject

/**
 * Bare X (Twitter) and Bluesky post links show the post in the text: author, date, text and its
 * first photo or video still. Tapping the post opens the link, as tapping the link did.
 *
 * X posts come from X's embed service, the one embedded posts on web pages use. Long posts are cut
 * there, and end with "…". Bluesky posts come from Bluesky's public API.
 */
internal object SocialPosts {
    private const val TAG = "SocialPosts"
    private val STATUS =
        Pattern.compile(
            "^https?://(?:www\\.|mobile\\.)?(?:x|twitter)\\.com/(?:[A-Za-z0-9_]+|i/web)/status(?:es)?/(\\d+)",
            Pattern.CASE_INSENSITIVE,
        )
    private const val SYNDICATION_URL =
        "https://cdn.syndication.twimg.com/tweet-result?id=%s&token=%s"
    private val BLUESKY_POST =
        Pattern.compile(
            "^https?://(?:www\\.)?bsky\\.app/profile/([^/?#]+)/post/([A-Za-z0-9]+)",
            Pattern.CASE_INSENSITIVE,
        )
    private const val BLUESKY_URL =
        "https://public.api.bsky.app/xrpc/app.bsky.feed.getPostThread" +
            "?depth=0&parentHeight=0&uri=at://%s/app.bsky.feed.post/%s"
    private const val X_KEY = "x:"
    private const val BLUESKY_KEY = "bluesky:"
    private const val CACHE_SIZE = 100
    private const val IMAGE_WIDTH_DP = 200

    // Tall photos are shown narrower, at most this many times as tall as the full width.
    private const val MAX_IMAGE_RATIO = 1.25f

    // Cached for posts that don't exist anymore or can't be shown (deleted, protected).
    private val MISSING = Post("", "", "", null, null, 0, 0, false)

    private val posts: MutableMap<String, Post> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, Post>(32, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, Post>?
                ): Boolean = size > CACHE_SIZE
            }
        )
    private val loader = Executors.newFixedThreadPool(2)

    /** What is shown of a post. [imageUrl] is its first photo or video still, or null. */
    class Post(
        val name: String,
        val handle: String,
        val text: String,
        val date: Date?,
        val imageUrl: String?,
        val imageWidth: Int,
        val imageHeight: Int,
        val video: Boolean,
    )

    /** A link waiting for its post. */
    class Pending(val spinner: ImageSpan, val link: URLSpan, val id: String)

    /** A photo shown in a post, loaded after the post. */
    class Image(
        val placeholder: ImageSpan,
        val url: String,
        val width: Int,
        val height: Int,
        val video: Boolean,
    )

    /**
     * Identifies the post of an X, Twitter or Bluesky link ("x:id" or "bluesky:handle/id"), or
     * null.
     */
    fun postKey(url: String?, x: Boolean, bluesky: Boolean): String? {
        if (url == null) return null
        var matcher = STATUS.matcher(url)
        if (x && matcher.find()) return X_KEY + matcher.group(1)
        matcher = BLUESKY_POST.matcher(url)
        if (bluesky && matcher.find()) {
            return BLUESKY_KEY + matcher.group(1) + "/" + matcher.group(2)
        }
        return null
    }

    /** The text shows the URL itself, not words the author linked. */
    fun isBareLink(linkText: String?): Boolean {
        if (linkText == null) return false
        val text = linkText.trim().lowercase(Locale.ROOT)
        return text.isNotEmpty() &&
            !text.matches(Regex(".*\\s.*")) &&
            (text.contains("x.com/") || text.contains("twitter.com/") || text.contains("bsky.app/"))
    }

    /**
     * Applied to the new text before it is set: known posts replace their link right away, the
     * others get a spinner after the link. [images] gets the photos of the posts shown right away.
     */
    fun prepare(
        textView: TextView,
        builder: SpannableStringBuilder,
        links: List<URLSpan>,
        loadImages: Boolean,
        images: MutableList<Image>,
    ): List<Pending> {
        val pending = ArrayList<Pending>()
        if (links.isEmpty()) return pending
        val size = Math.round(textView.textSize * 0.85f)
        for (link in YoutubeTitles.fromTheEnd(builder, links)) {
            val start = builder.getSpanStart(link)
            val end = builder.getSpanEnd(link)
            if (start < 0 || end <= start) continue
            val id = postKey(CommentImages.normalizeLink(link.url), true, true) ?: continue
            val post = posts[id]
            if (post != null) {
                if (post !== MISSING) {
                    replaceWithPost(textView, builder, link, post, loadImages)?.let {
                        images.add(it)
                    }
                }
                continue
            }
            val spinner =
                ImageSpan(
                    YoutubeTitles.SpinnerDrawable(textView.currentTextColor, size),
                    ImageSpan.ALIGN_BOTTOM,
                )
            YoutubeTitles.insertAfterKeepingSpans(builder, start, end, " ￼")
            builder.setSpan(spinner, end + 1, end + 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            pending.add(Pending(spinner, link, id))
        }
        return pending
    }

    /** Looks the posts up in the background and swaps them in while the view shows this text. */
    fun load(textView: TextView, generation: Any, pending: List<Pending>, loadImages: Boolean) {
        if (pending.isEmpty()) return
        YoutubeTitles.animate(textView, generation) { text ->
            pending.any { text.getSpanStart(it.spinner) >= 0 }
        }
        for (p in pending) {
            loader.execute {
                val post = fetch(p.id)
                textView.post { apply(textView, generation, p, post, loadImages) }
            }
        }
    }

    private fun apply(
        textView: TextView,
        generation: Any,
        p: Pending,
        post: Post?,
        loadImages: Boolean,
    ) {
        try {
            if (!CommentImages.isCurrent(textView, generation)) return
            val current = CommentImages.fullText(textView) as? Spanned ?: return
            if (current.getSpanStart(p.spinner) < 0) return

            val builder = SpannableStringBuilder(current)
            val spinnerStart = builder.getSpanStart(p.spinner)
            builder.removeSpan(p.spinner)
            // The space and the spinner.
            builder.delete(spinnerStart - 1, spinnerStart + 1)
            var image: Image? = null
            if (post != null && post !== MISSING && builder.getSpanStart(p.link) >= 0) {
                image = replaceWithPost(textView, builder, p.link, post, loadImages)
            }
            textView.setText(builder, TextView.BufferType.SPANNABLE)
            if (image != null) loadImages(textView, generation, listOf(image))
        } catch (exception: Exception) {
            Log.i(TAG, "Could not show the post", exception)
        }
    }

    /**
     * Replaces the link with the post, on lines of its own, with a bar on its left like a quote.
     * Returns the photo to load, or null.
     */
    private fun replaceWithPost(
        textView: TextView,
        builder: SpannableStringBuilder,
        link: URLSpan,
        post: Post,
        loadImages: Boolean,
    ): Image? {
        var start = builder.getSpanStart(link)
        val end = builder.getSpanEnd(link)
        val textColor = textView.currentTextColor
        val mutedColor = (textColor and 0x00FFFFFF) or 0x99000000.toInt()

        val block = SpannableStringBuilder()
        block.append(post.name)
        block.setSpan(StyleSpan(Typeface.BOLD), 0, block.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        block.append(" @").append(post.handle)
        formatDate(post.date)?.let { block.append(" · ").append(it) }
        // The first line looks like a link, so the post reads as one.
        block.setSpan(
            ForegroundColorSpan(textView.linkTextColors.defaultColor),
            0,
            block.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        block.setSpan(UnderlineSpan(), 0, block.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (post.text.isNotEmpty()) block.append('\n').append(post.text)

        var image: Image? = null
        if (post.imageUrl != null && loadImages) {
            val size = imageSize(textView, post.imageWidth, post.imageHeight)
            block.append("\n￼")
            val box = GradientDrawable()
            box.setColor((textColor and 0x00FFFFFF) or 0x1A000000)
            box.cornerRadius = dp(textView, 6).toFloat()
            box.setBounds(0, 0, size[0], size[1])
            val placeholder = ImageSpan(box, ImageSpan.ALIGN_BOTTOM)
            block.setSpan(
                placeholder,
                block.length - 1,
                block.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            image = Image(placeholder, post.imageUrl, size[0], size[1], post.video)
        }

        // The post opens the link like the link did, without being drawn as one (colored and
        // underlined).
        for (span in builder.getSpans(start, end, Any::class.java)) {
            if (builder.getSpanStart(span) == start && builder.getSpanEnd(span) == end) {
                builder.removeSpan(span)
            }
        }
        builder.replace(start, end, block)
        var blockEnd = start + block.length
        builder.setSpan(PlainLinkSpan(link.url), start, blockEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        // Lines of its own: a line break before and after the post, unless the text has one.
        if (blockEnd < builder.length && builder[blockEnd] != '\n') builder.insert(blockEnd, "\n")
        if (start > 0 && builder[start - 1] != '\n') {
            builder.insert(start, "\n")
            start++
            blockEnd++
        }
        builder.setSpan(
            QuoteBar(mutedColor, dp(textView, 3), dp(textView, 10)),
            start,
            blockEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        return image
    }

    fun loadImages(textView: TextView, generation: Any, images: List<Image>) {
        for (image in images) {
            loader.execute {
                try {
                    val drawable = GlideUtils.fetch(textView, image.url, image.width, image.height)
                    textView.post { showImage(textView, generation, image, drawable) }
                } catch (exception: Exception) {
                    Log.i(TAG, "Could not load the photo of the post", exception)
                }
            }
        }
    }

    private fun showImage(textView: TextView, generation: Any, image: Image, drawable: Drawable) {
        if (!CommentImages.isCurrent(textView, generation)) return
        val text = CommentImages.fullText(textView) as? Spannable ?: return
        val start = text.getSpanStart(image.placeholder)
        val end = text.getSpanEnd(image.placeholder)
        if (start < 0) return
        val shown =
            if (image.video) {
                YoutubeTitles.PlayOverlay(drawable, YoutubeTitles.PlayDrawable(dp(textView, 44)))
            } else {
                drawable
            }
        shown.setBounds(0, 0, image.width, image.height)
        text.removeSpan(image.placeholder)
        text.setSpan(
            ImageSpan(shown, ImageSpan.ALIGN_BOTTOM),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        CommentImages.fullTextChanged(textView, text)
    }

    /**
     * The photo's size in the text: a YouTube thumbnail's width, narrower when the photo is tall.
     */
    private fun imageSize(textView: TextView, width: Int, height: Int): IntArray {
        val baseWidth =
            if (CommentImages.fullWidthDefault()) contentWidth(textView)
            else dp(textView, IMAGE_WIDTH_DP)
        val ratio = if (width > 0 && height > 0) height.toFloat() / width else 9f / 16f
        var w = baseWidth
        var h = Math.round(w * ratio)
        val maxHeight = Math.round(baseWidth * MAX_IMAGE_RATIO)
        if (h > maxHeight) {
            h = maxHeight
            w = Math.round(h / ratio)
        }
        return intArrayOf(Math.max(1, w), Math.max(1, h))
    }

    /** The post, MISSING when there is none for this key, or null after network errors. */
    private fun fetch(id: String): Post? {
        val x = id.startsWith(X_KEY)
        var attempt = 1
        while (true) {
            var connection: HttpURLConnection? = null
            try {
                val url =
                    if (x) {
                        val status = id.substring(X_KEY.length)
                        String.format(SYNDICATION_URL, status, token(status))
                    } else {
                        val parts = id.substring(BLUESKY_KEY.length).split("/", limit = 2)
                        String.format(BLUESKY_URL, URLEncoder.encode(parts[0], "UTF-8"), parts[1])
                    }
                connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                val code = connection.responseCode
                if (code in 400..499 && code != 408 && code != 429) {
                    // Deleted, protected or not a post: asking again won't change it.
                    posts[id] = MISSING
                    return MISSING
                }
                if (code != 200) throw IOException("HTTP $code")
                val body = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                val json = JSONObject(body)
                val post = if (x) parseX(json) else parseBluesky(json)
                posts[id] = post
                return post
            } catch (exception: Exception) {
                if (attempt >= GlideUtils.MAX_ATTEMPTS) {
                    Log.i(TAG, "Could not load the post $id", exception)
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

    private fun parseX(json: JSONObject): Post {
        val user = json.optJSONObject("user")
        // An empty answer: no such post.
        if (user == null || !json.has("text")) return MISSING
        var text = json.optString("text", "")

        // The text ends with links to its photos (t.co), outside the displayed range.
        val range = json.optJSONArray("display_text_range")
        if (range != null && range.length() == 2) {
            val endPoint =
                Math.min(range.optInt(1, text.length), text.codePointCount(0, text.length))
            text = text.substring(0, text.offsetByCodePoints(0, endPoint))
        }
        // Short links show where they go.
        val urls = json.optJSONObject("entities")?.optJSONArray("urls")
        if (urls != null) {
            for (i in 0 until urls.length()) {
                val url = urls.getJSONObject(i)
                val shortUrl = url.optString("url", "")
                if (shortUrl.isNotEmpty()) {
                    text = text.replace(shortUrl, url.optString("display_url", shortUrl))
                }
            }
        }
        text = unescape(text).trim()
        // Long posts are cut by the embed service.
        if (json.has("note_tweet") && !text.endsWith("…")) text += "…"

        var imageUrl: String? = null
        var width = 0
        var height = 0
        var video = false
        val media = json.optJSONArray("mediaDetails")
        if (media != null && media.length() > 0) {
            val first = media.getJSONObject(0)
            imageUrl = optStringOrNull(first, "media_url_https")
            first.optJSONObject("original_info")?.let {
                width = it.optInt("width")
                height = it.optInt("height")
            }
            val type = first.optString("type", "")
            video = type == "video" || type == "animated_gif"
            if (imageUrl != null && imageUrl.startsWith("https://pbs.twimg.com/")) {
                imageUrl += "?name=small"
            }
        }

        return Post(
            user.optString("name", ""),
            user.optString("screen_name", ""),
            text,
            parseDate(json.optString("created_at", "")),
            imageUrl,
            width,
            height,
            video,
        )
    }

    /** A Bluesky post (getPostThread). Its first image, video still or link preview is shown. */
    private fun parseBluesky(json: JSONObject): Post {
        // Deleted or blocked.
        val post = json.optJSONObject("thread")?.optJSONObject("post") ?: return MISSING
        val author = post.optJSONObject("author") ?: return MISSING
        val record = post.optJSONObject("record") ?: return MISSING
        val handle = author.optString("handle", "")
        val name = author.optString("displayName", "").trim()

        var imageUrl: String? = null
        var width = 0
        var height = 0
        var video = false
        var embed = post.optJSONObject("embed")
        // A quote with media.
        if (embed != null && embed.has("media")) embed = embed.optJSONObject("media")
        if (embed != null) {
            val type = embed.optString("\$type", "")
            var ratio: JSONObject? = null
            if (type.startsWith("app.bsky.embed.images")) {
                val images = embed.optJSONArray("images")
                val first =
                    if (images == null || images.length() == 0) null else images.getJSONObject(0)
                if (first != null) {
                    imageUrl = optStringOrNull(first, "thumb")
                    ratio = first.optJSONObject("aspectRatio")
                }
            } else if (type.startsWith("app.bsky.embed.video")) {
                imageUrl = optStringOrNull(embed, "thumbnail")
                ratio = embed.optJSONObject("aspectRatio")
                video = true
            } else if (type.startsWith("app.bsky.embed.external")) {
                embed.optJSONObject("external")?.let { imageUrl = optStringOrNull(it, "thumb") }
            }
            if (ratio != null) {
                width = ratio.optInt("width")
                height = ratio.optInt("height")
            }
        }

        return Post(
            name.ifEmpty { handle },
            handle,
            record.optString("text", "").trim(),
            parseDate(record.optString("createdAt", "")),
            imageUrl,
            width,
            height,
            video,
        )
    }

    // JSONObject.optString(name, null) on Android: the value, or null when there is none.
    private fun optStringOrNull(json: JSONObject, name: String): String? =
        if (json.isNull(name)) null else json.optString(name)

    /** The token X's embed script sends with the post id. */
    private fun token(id: String): String {
        val value = id.toDouble() / 1e15 * Math.PI
        val result = StringBuilder(java.lang.Long.toString(value.toLong(), 36))
        var fraction = value - Math.floor(value)
        var i = 0
        while (i < 10 && fraction > 0) {
            fraction *= 36
            val digit = fraction.toInt()
            result.append(Character.forDigit(digit, 36))
            fraction -= digit
            i++
        }
        val token = result.toString().replace("0", "")
        return token.ifEmpty { "a" }
    }

    /** An ISO 8601 time in UTC ("2026-10-02T15:22:58.000Z"); the fraction of seconds is ignored. */
    private fun parseDate(value: String): Date? =
        try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.parse(value.substring(0, 19))
        } catch (exception: Exception) {
            null
        }

    /** "Oct 2", or "Oct 2, 2025" for another year. */
    private fun formatDate(date: Date?): String? {
        if (date == null) return null
        val then = Calendar.getInstance()
        then.time = date
        val thisYear = then.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)
        return SimpleDateFormat(if (thisYear) "MMM d" else "MMM d, yyyy", Locale.getDefault())
            .format(date)
    }

    private fun unescape(text: String): String =
        text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")

    private fun contentWidth(textView: TextView): Int {
        var width = textView.width - textView.totalPaddingLeft - textView.totalPaddingRight
        if (width <= 0) width = textView.resources.displayMetrics.widthPixels - dp(textView, 32)
        // The quote bar and its gap.
        return width - dp(textView, 13)
    }

    /** Opens the link when tapped, like a URLSpan, but keeps the text's own style. */
    private class PlainLinkSpan(url: String?) : URLSpan(url) {
        override fun updateDrawState(paint: TextPaint) {}
    }

    /** A bar on the left of the post's lines, like a quote. */
    private class QuoteBar(private val color: Int, private val width: Int, private val gap: Int) :
        LeadingMarginSpan {
        override fun getLeadingMargin(first: Boolean): Int = width + gap

        override fun drawLeadingMargin(
            canvas: Canvas,
            paint: Paint,
            x: Int,
            dir: Int,
            top: Int,
            baseline: Int,
            bottom: Int,
            text: CharSequence?,
            start: Int,
            end: Int,
            first: Boolean,
            layout: Layout?,
        ) {
            val style = paint.style
            val previousColor = paint.color
            paint.style = Paint.Style.FILL
            paint.color = color
            canvas.drawRect(
                x.toFloat(),
                top.toFloat(),
                (x + dir * width).toFloat(),
                bottom.toFloat(),
                paint,
            )
            paint.style = style
            paint.color = previousColor
        }
    }
}
