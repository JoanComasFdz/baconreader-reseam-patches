// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("FixListThumbnails")

package com.joancomasfdz.baconreader.thumbnails

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.Log
import android.widget.ImageView
import app.reseam.runtime.Export
import com.bumptech.glide.RequestBuilder
import com.joancomasfdz.baconreader.common.GlideUtils
import com.onelouder.baconreader.Preferences
import com.onelouder.baconreader.media.gfycat.RedGifsManager
import com.onelouder.baconreader.reddit.Link
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/*
 * Reddit sends list thumbnails as signed preview.redd.it URLs
 * ("...?width=140&amp;height=132&amp;auto=webp&amp;s=SIGNATURE"). BaconReader does not request
 * raw_json, so the "&" arrive HTML-escaped, and its thumbnail loader uses them as they are: the
 * server cannot find the signature and answers 403, leaving the thumbnail empty.
 *
 * Link posts without a thumbnail get one from Reddit's preview image, the embed, or YouTube. NSFW
 * (with "Show NSFW images" off) and spoiler posts get a blurred one. Crossposts of galleries get
 * the preview of the first image, and BaconReader's own gallery indicator. RedGIFs posts without a
 * thumbnail get theirs from the RedGIFs API when they are shown.
 */

private const val TAG = "FixListThumbnails"

/** Smallest preview width used as a list thumbnail: the list shows them at about 70dp. */
private const val MIN_PREVIEW_WIDTH = 216
private val WIDTH = Regex("[?&]width=(\\d+)")

// The blur is remembered in the URL fragment, which is never sent to the server, so it survives
// the app saving the link in its database.
private const val NSFW = "#patches-blur-nsfw"
private const val SPOILER = "#patches-blur-spoiler"

/** A RedGIFs link whose thumbnail is looked up when the row is shown. */
private const val REDGIFS = "#patches-redgifs"
private val REDGIFS_ID =
    Regex(
        "^https?://(?:[a-z0-9]+\\.)?redgifs\\.com/(?:watch|ifr|i)/([A-Za-z]+)",
        RegexOption.IGNORE_CASE,
    )

/** RedGIFs id to thumbnail URL, or "" when the video has none (deleted). */
private val redgifsThumbnails = ConcurrentHashMap<String, String>()

/** The post each thumbnail view waits a RedGIFs thumbnail for. Only used on the main thread. */
private val redgifsLoading = WeakHashMap<ImageView, Link>()
private val redgifsLoader = Executors.newFixedThreadPool(2)

private const val BLUR_RADIUS_DP = 12f
private val VIDEO_ID =
    Regex("(?:youtu\\.be/|/(?:shorts|live|embed|v)/)([A-Za-z0-9_-]{11})|[?&]v=([A-Za-z0-9_-]{11})")

/** Fixes the thumbnail URL of the post, at the start of the app's thumbnail checks and loads. */
@Export
fun fixThumbnail(link: Link?) {
    if (link == null) return
    try {
        val thumbnail = link.thumbnail
        val fixed = decide(link, unescape(thumbnail))
        if (fixed != null && fixed != thumbnail) link.thumbnail = fixed
    } catch (exception: Exception) {
        Log.i(TAG, "Could not fix the thumbnail URL", exception)
    }
}

/**
 * NSFW posts show their preview when the app's "Show NSFW images" is on, and a blurred one when it
 * is off. Spoilers are always blurred.
 */
private fun decide(link: Link, thumbnail: String?): String? {
    val marker = marker(thumbnail)
    var plain = withoutMarker(thumbnail)
    val lower = plain?.trim()?.lowercase(Locale.ROOT) ?: ""
    val spoiler = marker == SPOILER || lower == "spoiler"
    val nsfw = marker == NSFW || lower == "nsfw" || link.over_18 == true
    val blur = spoiler || (nsfw && !showNsfwImages())

    // Anything but a URL ("default", "image", "nsfw", "spoiler"...) means there is no thumbnail.
    val missing = plain == null || !lower.startsWith("http")
    if (missing && link.is_self != true && (!blur || canBlur())) {
        val found = findThumbnail(link)
        if (found != null) {
            plain = found
        } else {
            // The thumbnail needs the video's name as RedGIFs writes it, which only its API gives.
            val redgifs = redgifsUrl(link)
            if (redgifs != null) plain = redgifs + REDGIFS
        }
    }
    if (plain == null || !plain.startsWith("http") || !blur || !canBlur()) return plain
    return plain + if (spoiler) SPOILER else NSFW
}

/**
 * Whether the post shows BaconReader's gallery indicator, the lines above the thumbnail, in the
 * post list and the post header: crossposts of galleries are galleries too.
 */
@Export
fun isGallery(link: Link?): Boolean? {
    if (link == null) return false
    val gallery = link.isGallery
    if (gallery == true) return gallery
    if (crosspostParent(link)?.isGallery == true) return true
    return gallery ?: false
}

/** The post a crosspost shares, or null. */
private fun crosspostParent(link: Link): Link? = link.crosspostParents?.firstOrNull()

/**
 * Whether to show a blurred thumbnail where the app would hide the NSFW post's thumbnail. Runs at
 * the start of the app's check, after [fixThumbnail]. False lets the app decide.
 */
@Export fun forceShow(link: Link?): Boolean = marker(link?.thumbnail) != null

/**
 * Loads the thumbnail in place of the app's Glide load, trying again when it fails, and looks up
 * the thumbnails of RedGIFs posts.
 */
@Export
fun loadThumbnail(requestBuilder: RequestBuilder<*>?, imageView: ImageView?, link: Link?) {
    if (imageView == null) return
    if (link != null) {
        val thumbnail = link.thumbnail
        val plain = withoutMarker(thumbnail)
        val id = redgifsId(plain)
        if (plain != null && plain.endsWith(REDGIFS) && id != null) {
            loadRedgifsThumbnail(imageView, link, id, marker(thumbnail))
            return
        }
    }
    redgifsLoading.remove(imageView)
    var onFailed: Runnable? = null
    // Reddit's thumbnail of a RedGIFs video can be broken: ask RedGIFs for it.
    val redgifs = link?.let(::redgifsUrl)
    val id = redgifsId(redgifs)
    if (link != null && id != null) {
        val blurMarker = marker(link.thumbnail)
        onFailed = Runnable { loadRedgifsThumbnail(imageView, link, id, blurMarker) }
    }
    GlideUtils.intoWithRetry(requestBuilder, imageView, onFailed)
}

/** The RedGIFs link of a post, or of the post it crossposts, or null. */
private fun redgifsUrl(link: Link): String? {
    for (post in listOfNotNull(link, crosspostParent(link))) {
        for (url in listOf(post.url, post.urlOverriddenByDest)) {
            val unescaped = unescape(url)
            if (redgifsId(unescaped) != null) return unescaped
        }
    }
    return null
}

private fun loadRedgifsThumbnail(
    imageView: ImageView,
    link: Link,
    id: String,
    blurMarker: String?,
) {
    GlideUtils.clear(imageView)
    redgifsLoading[imageView] = link
    val known = redgifsThumbnails[id]
    if (known != null) {
        showRedgifsThumbnail(imageView, link, known, blurMarker)
        return
    }
    redgifsLoader.execute {
        // Network trouble: try again when the row is shown again.
        val found = fetchRedgifsThumbnail(id) ?: return@execute
        redgifsThumbnails[id] = found
        imageView.post { showRedgifsThumbnail(imageView, link, found, blurMarker) }
    }
}

private fun showRedgifsThumbnail(
    imageView: ImageView,
    link: Link,
    url: String,
    blurMarker: String?,
) {
    if (redgifsLoading[imageView] !== link || url.isEmpty()) return
    try {
        // Kept on the post, so the lookup is not needed the next time.
        link.thumbnail = if (blurMarker == null) url else url + blurMarker
        GlideUtils.load(imageView, url, null, null)
    } catch (exception: Exception) {
        Log.i(TAG, "Could not show the RedGIFs thumbnail", exception)
    }
}

/**
 * The thumbnail of a RedGIFs video from its API, with BaconReader's client and token: "" when the
 * video has none (deleted, not found), or null when the API could not be reached. Blocks.
 */
private fun fetchRedgifsThumbnail(id: String): String? {
    for (attempt in 1..GlideUtils.MAX_ATTEMPTS) {
        try {
            val response = RedGifsManager.INSTANCE.getRedGifInfo(id).execute()
            val code = response.code()
            if (code in 400..499 && code != 401 && code != 429) return ""
            val urls = response.body()?.gif?.urls
            if (urls != null) return urls.thumbnail ?: urls.poster ?: ""
        } catch (exception: Exception) {
            Log.i(TAG, "Could not get the RedGIFs thumbnail of $id", exception)
        }
        try {
            Thread.sleep(GlideUtils.RETRY_DELAY_MS * attempt)
        } catch (exception: InterruptedException) {
            return null
        }
    }
    return null
}

/** The id of a RedGIFs link, or null. */
private fun redgifsId(url: String?): String? = url?.let {
    REDGIFS_ID.find(it)?.groupValues?.get(1)?.lowercase(Locale.ROOT)
}

/** Blurs the row's thumbnail, or clears the blur, at the start of the app's thumbnail load. */
@Export
fun beforeLoad(link: Link?, imageView: ImageView?) {
    if (imageView == null || !canBlur()) return
    try {
        val blur = marker(link?.thumbnail) != null
        val radius = BLUR_RADIUS_DP * imageView.resources.displayMetrics.density
        imageView.setRenderEffect(
            if (blur) RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP) else null
        )
    } catch (exception: Exception) {
        Log.i(TAG, "Could not blur the thumbnail", exception)
    }
}

private fun marker(thumbnail: String?): String? =
    when {
        thumbnail == null -> null
        thumbnail.endsWith(SPOILER) -> SPOILER
        thumbnail.endsWith(NSFW) -> NSFW
        else -> null
    }

private fun withoutMarker(thumbnail: String?): String? {
    val marker = marker(thumbnail) ?: return thumbnail
    return thumbnail!!.substring(0, thumbnail.length - marker.length)
}

/** The blur needs Android 12; before that, NSFW and spoiler posts keep the app's icon. */
private fun canBlur(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** BaconReader's own "Show NSFW images" setting. */
private fun showNsfwImages(): Boolean =
    try {
        Preferences.getShowNSFWImages()
    } catch (throwable: Throwable) {
        false
    }

/**
 * Reddit's preview image, else the first image of a gallery, else the embed's thumbnail, else the
 * YouTube video thumbnail. Crossposts use the shared post's.
 */
private fun findThumbnail(link: Link): String? =
    findOwnThumbnail(link) ?: crosspostParent(link)?.let(::findOwnThumbnail)

private fun findOwnThumbnail(link: Link): String? {
    try {
        val image = link.preview?.images?.firstOrNull()
        if (image != null) {
            var best: String? = null
            for (resolution in image.resolutions.orEmpty()) {
                val url = resolution?.url ?: continue
                best = url
                if (previewWidth(url) >= MIN_PREVIEW_WIDTH) break
            }
            if (best == null) best = image.source?.url
            if (best != null && best.startsWith("http")) return unescape(best)
        }
        galleryPreview(link)?.let {
            return it
        }

        val embedThumbnail = link.media?.oembed?.thumbnail_url
        if (embedThumbnail != null && embedThumbnail.startsWith("http")) {
            return unescape(embedThumbnail)
        }

        val videoId = youtubeVideoId(unescape(link.url))
        if (videoId != null) return "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
    } catch (exception: Exception) {
        Log.i(TAG, "Could not find a thumbnail", exception)
    }
    return null
}

/** A preview of the first image of a Reddit gallery, or null. */
private fun galleryPreview(link: Link): String? {
    val items = link.redditGalleryData?.galleryItems
    val metadata = link.redditMediaMetadata
    if (items.isNullOrEmpty() || metadata == null) return null
    val previews = metadata[items[0]?.media_id]?.p ?: return null
    var best: String? = null
    for (preview in previews) {
        val url = preview?.u ?: continue
        best = url
        val width = preview.x?.toIntOrNull()
        if (width != null && width >= MIN_PREVIEW_WIDTH) break
    }
    return if (best != null && best.startsWith("http")) unescape(best) else null
}

/** The video id of a YouTube watch, youtu.be, shorts, live or embed link, or null. */
private fun youtubeVideoId(url: String?): String? {
    if (url == null) return null
    val host =
        url.lowercase(Locale.ROOT)
            .replaceFirst(Regex("^[a-z-]+://"), "")
            .replaceFirst(Regex("[/?#:].*$"), "")
    if (host != "youtu.be" && host != "youtube.com" && !host.endsWith(".youtube.com")) return null
    val match = VIDEO_ID.find(url) ?: return null
    return match.groups[1]?.value ?: match.groups[2]?.value
}

private fun previewWidth(url: String): Int =
    WIDTH.find(url)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE

private fun unescape(url: String?): String? {
    if (url == null || url.indexOf('&') < 0) return url
    return url.replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
}
