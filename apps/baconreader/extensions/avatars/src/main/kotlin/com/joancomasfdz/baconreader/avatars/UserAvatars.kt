// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("UserAvatars")

package com.joancomasfdz.baconreader.avatars

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.util.Log
import android.util.LruCache
import android.view.View
import android.widget.TextView
import android.widget.Toast
import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.joancomasfdz.baconreader.settings.UserAvatarsSettings
import com.onelouder.baconreader.RequestMethod
import com.onelouder.baconreader.data.SessionManager
import com.onelouder.baconreader.data.SessionToken
import com.onelouder.baconreader.reddit.Comment
import com.onelouder.baconreader.reddit.Message
import com.onelouder.baconreader.reddit.RedditApi
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.lang.reflect.InvocationTargetException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import org.json.JSONObject

/**
 * User avatars before the author of comments, as on Reddit's website. The comment JSON has the
 * author's id (`author_fullname`, a field the patch adds to the app's Message class). Avatars come
 * from `api/user_data_by_account_ids` (`profile_img`), up to 100 users per request.
 */
private object Avatars {
    const val TAG = "UserAvatars"
    const val USER_DATA_PATH = "api/user_data_by_account_ids"
    const val BATCH_SIZE = 100
    const val BATCH_DELAY_MS = 150L

    /** Avatars are drawn at about 20dp; this keeps them sharp on any screen. */
    const val BITMAP_SIZE = 128

    /** The size of the comment avatars of Reddit's website (Large). */
    const val REDDIT_SIZE_DP = 24

    val main = Handler(Looper.getMainLooper())
    val loader = Executors.newFixedThreadPool(3)

    @Volatile var context: Context? = null

    // The rest is guarded by this object.

    /** User name to user id (t2_...), from the comments shown, for the reply screen. */
    val ids = HashMap<String, String>()

    /** User id (t2_...) to avatar URL, or "" when the user has none. */
    val urls = HashMap<String, String>()
    val bitmaps = LruCache<String, Bitmap>(300)

    /** User ids whose avatar is being looked up or downloaded. */
    val loading = HashSet<String>()

    /** User ids to look up in the next request. */
    val queue = LinkedHashSet<String>()

    /** Spans waiting for their avatar. */
    val waiting = HashMap<String, MutableList<WeakReference<AvatarSpan>>>()
    var batchScheduled = false

    // Main thread only. The error shows once per session, so the user knows why there are no
    // avatars.
    var errorShown = false
}

/** Puts the avatar before the author line of a comment, which getCommentSubText returns. */
@Export
fun withAvatar(context: Context?, comment: Comment?, subText: Spannable?): Spannable? {
    try {
        if (subText == null || comment == null || !UserAvatarsSettings.userAvatars.value) {
            return subText
        }
        remember(context)
        val userId = comment.author_fullname
        if (userId.isNullOrEmpty() || !userId.startsWith("t2_")) return subText
        comment.author?.let { synchronized(Avatars) { Avatars.ids[it] = userId } }

        val span = AvatarSpan()
        val text = SpannableStringBuilder("￼ ")
        text.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.append(subText)
        request(userId, span)
        return text
    } catch (ex: Exception) {
        Log.i(Avatars.TAG, "Could not add the avatar", ex)
        return subText
    }
}

/** The view to redraw when the avatar arrives, after CommentHolder sets the author line. */
@Export
fun bindAvatars(textView: TextView?) {
    val text = textView?.text as? Spanned ?: return
    for (span in text.getSpans(0, text.length, AvatarSpan::class.java)) {
        span.view = WeakReference(textView)
    }
}

/**
 * Puts the avatar before the author on the reply screen, after it sets "Reply to [author] in r/...
 * as [you]". The comment reaches that screen without its author id, so the user is found by name.
 */
@Export
fun replyAvatar(textView: TextView?) {
    try {
        if (textView == null || !UserAvatarsSettings.userAvatars.value) return
        remember(textView.context)
        val spanned = textView.text as? Spanned ?: return
        // The author is the first colored part.
        val first =
            spanned.getSpans(0, spanned.length, ForegroundColorSpan::class.java).minByOrNull {
                spanned.getSpanStart(it)
            } ?: return
        val start = spanned.getSpanStart(first)
        val author = spanned.subSequence(start, spanned.getSpanEnd(first)).toString()
        insertAvatar(textView, start, author, null)
    } catch (ex: Exception) {
        Log.i(Avatars.TAG, "Could not add the avatar to the reply screen", ex)
    }
}

/**
 * Puts the avatars of the message's author and, for private messages, of its recipient before their
 * names, after an inbox item sets its label ("Comment reply: [author]", "to [recipient], from
 * [author]").
 */
@Export
fun inboxAvatar(message: Message?, label: TextView?) {
    try {
        if (message == null || label == null || !UserAvatarsSettings.userAvatars.value) return
        remember(label.context)
        val text = label.text.toString()
        val isComment = message is Comment

        // The sender: "Comment reply: [author]" or "from [author]", which isn't shown when the
        // user is the sender.
        val author = message.author
        var authorStart = -1
        if (author != null) {
            if (isComment) {
                authorStart = text.lastIndexOf(author)
            } else {
                val from = text.lastIndexOf("from $author")
                if (from >= 0) authorStart = from + "from ".length
            }
        }
        // The recipient of a private message, "to [user]", in Messages and Sent. Not a
        // subreddit's mod mail ("#subreddit").
        val dest = if (isComment) null else message.dest
        var destStart = -1
        if (dest != null && !dest.startsWith("#") && !dest.contains("/")) {
            // The last one: the subject before it can contain "to ".
            val to = text.lastIndexOf("to $dest")
            if (to >= 0) destStart = to + "to ".length
        }

        // The later one first, so the position of the other one doesn't move.
        val authorId = message.author_fullname
        if (authorStart > destStart) {
            insertAvatar(label, authorStart, author!!, authorId)
            if (destStart >= 0) insertAvatar(label, destStart, dest!!, null)
        } else {
            if (destStart >= 0) insertAvatar(label, destStart, dest!!, null)
            if (authorStart >= 0) insertAvatar(label, authorStart, author!!, authorId)
        }
    } catch (ex: Exception) {
        Log.i(Avatars.TAG, "Could not add the avatar to the inbox", ex)
    }
}

private fun remember(context: Context?) {
    if (Avatars.context == null && context != null) Avatars.context = context.applicationContext
}

/**
 * Puts the avatar of the user at [start] of the text of the view. Without the user's id, it comes
 * from the comments already shown, or else from user/[name]/about.
 */
private fun insertAvatar(textView: TextView, start: Int, author: String, userId: String?) {
    if (author.isEmpty() || author == "[deleted]") return
    val span = AvatarSpan()
    span.view = WeakReference(textView)
    val withAvatar = SpannableStringBuilder(textView.text)
    withAvatar.insert(start, "￼ ")
    withAvatar.setSpan(span, start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    textView.text = withAvatar

    val known =
        synchronized(Avatars) {
            if (userId != null && userId.startsWith("t2_")) {
                Avatars.ids[author] = userId
                userId
            } else {
                Avatars.ids[author]
            }
        }
    if (known != null) {
        request(known, span)
        return
    }
    Avatars.loader.execute {
        try {
            val about = JSONObject(apiGet("user/$author/about", HashMap()))
            val id = about.getJSONObject("data").optString("id", "")
            if (id.isEmpty()) return@execute
            val found = "t2_$id"
            synchronized(Avatars) { Avatars.ids[author] = found }
            Avatars.main.post { request(found, span) }
        } catch (ex: Exception) {
            Log.i(Avatars.TAG, "Could not look up the user $author", ex)
            showError(ex)
        }
    }
}

private fun request(userId: String, span: AvatarSpan) {
    synchronized(Avatars) {
        val bitmap = Avatars.bitmaps.get(userId)
        if (bitmap != null) {
            span.bitmap = bitmap
            return
        }
        val url = Avatars.urls[userId]
        if (url == "") return // No avatar.

        Avatars.waiting.getOrPut(userId) { ArrayList() }.add(WeakReference(span))
        if (!Avatars.loading.add(userId)) return

        if (url != null) {
            download(userId, url)
            return
        }
        Avatars.queue.add(userId)
        if (!Avatars.batchScheduled) {
            Avatars.batchScheduled = true
            Avatars.main.postDelayed({ lookUpQueue() }, Avatars.BATCH_DELAY_MS)
        }
    }
}

/** Looks up the queued users, 100 per request. */
private fun lookUpQueue() {
    val ids =
        synchronized(Avatars) {
            Avatars.batchScheduled = false
            val queued = ArrayList(Avatars.queue)
            Avatars.queue.clear()
            queued
        }
    for (batch in ids.chunked(Avatars.BATCH_SIZE)) {
        Avatars.loader.execute { lookUp(batch) }
    }
}

private fun lookUp(ids: List<String>) {
    val found = HashMap<String, String>()
    try {
        val users =
            JSONObject(apiGet(Avatars.USER_DATA_PATH, mapOf("ids" to ids.joinToString(","))))
        for (id in ids) {
            val user = users.optJSONObject(id)
            found[id] = user?.optString("profile_img", "")?.replace("&amp;", "&") ?: ""
        }
    } catch (ex: Exception) {
        Log.i(Avatars.TAG, "Could not look up the avatars of ${ids.size} users", ex)
        showError(ex)
    }
    synchronized(Avatars) {
        for (id in ids) {
            val url = found[id]
            if (url == null) {
                // The request failed: try again for the next comment of this user.
                Avatars.loading.remove(id)
                Avatars.waiting.remove(id)
                continue
            }
            Avatars.urls[id] = url
            if (url.isEmpty()) {
                Avatars.loading.remove(id)
                Avatars.waiting.remove(id)
            } else {
                download(id, url)
            }
        }
    }
}

private fun download(userId: String, url: String) {
    Avatars.loader.execute {
        var bitmap: Bitmap? = null
        try {
            val data = get(url)
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(data, 0, data.size, options)
            options.inSampleSize = 1
            while (
                minOf(options.outWidth, options.outHeight) / (options.inSampleSize * 2) >=
                    Avatars.BITMAP_SIZE
            ) {
                options.inSampleSize *= 2
            }
            options.inJustDecodeBounds = false
            bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, options)
        } catch (ex: Exception) {
            Log.i(Avatars.TAG, "Could not load the avatar $url", ex)
            showError(ex)
        }
        val loaded = bitmap
        Avatars.main.post { deliver(userId, loaded) }
    }
}

private fun showError(ex: Exception) {
    val cause = (ex as? InvocationTargetException)?.cause ?: ex
    val message = cause.message ?: cause.javaClass.simpleName
    Avatars.main.post {
        if (Avatars.errorShown) return@post
        val context = Avatars.context ?: return@post
        Avatars.errorShown = true
        Toast.makeText(context, BaconReaderStrings.userAvatarsError(message), Toast.LENGTH_LONG)
            .show()
    }
}

private fun deliver(userId: String, bitmap: Bitmap?) {
    val spans =
        synchronized(Avatars) {
            Avatars.loading.remove(userId)
            val spans = Avatars.waiting.remove(userId)
            if (bitmap == null) return
            Avatars.bitmaps.put(userId, bitmap)
            spans
        } ?: return
    for (reference in spans) {
        val span = reference.get() ?: continue
        span.bitmap = bitmap
        span.view?.get()?.invalidate()
    }
}

/**
 * A GET to Reddit's API through the app's own request code, RedditApi.httpRequest. With a login, it
 * calls oauth.reddit.com with the session's token, refreshed when needed, and the app's user agent.
 * Reddit blocks other requests from apps.
 */
private fun apiGet(path: String, params: Map<String, String>): String {
    val context = Avatars.context ?: throw IOException("No context yet")
    val token = SessionManager.getToken()
    val httpRequest =
        RedditApi::class
            .java
            .getDeclaredMethod(
                "httpRequest",
                Context::class.java,
                RequestMethod::class.java,
                String::class.java,
                Map::class.java,
                SessionToken::class.java,
            )
    httpRequest.isAccessible = true
    // Without a login the app calls www.reddit.com, which needs the .json suffix.
    val fullPath = if (token == null) "$path.json" else path
    return httpRequest.invoke(null, context, RequestMethod.GET, fullPath, HashMap(params), token)
        as String
}

private fun get(url: String): ByteArray {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("User-Agent", "android:com.onelouder.baconreader")
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code for $url")
        connection.inputStream.use { input ->
            val out = ByteArrayOutputStream()
            input.copyTo(out, 16384)
            return out.toByteArray()
        }
    } finally {
        connection.disconnect()
    }
}

/**
 * The avatar diameter in pixels for a text line of the given height, for the "Avatar size" setting:
 * Normal is the text line height, Large the size of Reddit's website, Medium halfway between them,
 * and Extra large one such step bigger than Large.
 */
private fun avatarSize(lineHeight: Int): Int {
    val density = Resources.getSystem().displayMetrics.density
    val normal = lineHeight.toFloat()
    // With a big font the text line can be as tall as Reddit's avatars: Large is still bigger than
    // Normal.
    val large = maxOf(Avatars.REDDIT_SIZE_DP * density, normal + 4 * density)
    val step = (large - normal) / 2f
    val size = (UserAvatarsSettings.userAvatarSize.value.toIntOrNull() ?: 0).coerceIn(0, 3)
    return Math.round(normal + size * step)
}

/**
 * A round avatar, as tall as the text line (Normal) or bigger, centered on the text, with a faint
 * disc until it's loaded, and behind it when enabled. A bigger avatar makes its line taller.
 */
private class AvatarSpan : ReplacementSpan() {
    @Volatile var bitmap: Bitmap? = null
    var view: WeakReference<View>? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()

    override fun getSize(
        textPaint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val metrics = textPaint.fontMetricsInt
        val lineHeight = metrics.descent - metrics.ascent
        val size = avatarSize(lineHeight)
        if (fm != null) {
            val extra = size - lineHeight
            fm.ascent = metrics.ascent - extra / 2
            fm.descent = metrics.descent + (extra - extra / 2)
            fm.top = minOf(metrics.top, fm.ascent)
            fm.bottom = maxOf(metrics.bottom, fm.descent)
        }
        return size
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        textPaint: Paint,
    ) {
        val metrics = textPaint.fontMetricsInt
        val size = avatarSize(metrics.descent - metrics.ascent).toFloat()
        val radius = size / 2f
        val cx = x + radius
        // The middle of the text, so the avatar stays centered when it's taller than the text.
        val cy = y + (metrics.ascent + metrics.descent) / 2f
        val current = bitmap
        // A faint disc while loading, and behind the avatar when enabled: many avatars are
        // transparent.
        if (current == null || UserAvatarsSettings.userAvatarBackground.value) {
            paint.shader = null
            paint.color = textPaint.color
            paint.alpha = 64 // 25%.
            canvas.drawCircle(cx, cy, radius, paint)
            if (current == null) return
        }
        val shader = BitmapShader(current, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = size / minOf(current.width, current.height)
        matrix.reset()
        matrix.setScale(scale, scale)
        matrix.postTranslate(cx - current.width * scale / 2f, cy - current.height * scale / 2f)
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        paint.alpha = 255
        canvas.drawCircle(cx, cy, radius, paint)
    }
}
