// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.embedcommentimages

import android.app.Dialog
import android.app.DownloadManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Environment
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.text.style.URLSpan
import android.util.Log
import android.util.LruCache
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.joancomasfdz.baconreader.common.DownloadProgress
import com.joancomasfdz.baconreader.common.ErrorDialog
import com.joancomasfdz.baconreader.common.GlideUtils
import com.joancomasfdz.baconreader.common.ImageDownloader
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.joancomasfdz.baconreader.settings.EmbedCommentImagesSettings
import com.onelouder.baconreader.ExpandableTextView
import java.io.File
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.Executors
import java.util.regex.Pattern

/** An image found in the text. [width] and [height] come from Reddit's attributes, or are 0. */
internal class ImageRef(
    val previewUrl: String,
    val fullUrl: String,
    val width: Int,
    val height: Int,
)

/**
 * Shows images from comments and post text as images under the text, instead of the "GIF" word or a
 * bare image link. Small images (emotes) are shown inline in the text. Videos uploaded in comments
 * are played under the text.
 *
 * Gestures on an image: tap switches between 200x200dp and full width, double tap opens a
 * full-screen zoomable viewer, long press downloads (when enabled in the settings).
 */
internal object CommentImages {
    private const val TAG = "EmbedCommentImages"

    const val IMAGE_SIZE_DP = 200
    private const val PLACEHOLDER_LINES = 2
    private const val EXPANDED_MAX_SCREEN_HEIGHT = 0.8f

    // <img> tags up to this size, in Reddit's width and height attributes, are emotes.
    private const val EMOTE_MAX_SIZE = 64

    private val IMG_TAG = Pattern.compile("<img\\b[^>]*?\\bsrc=\"([^\"]+)\"[^>]*>")
    private val WIDTH_ATTR = Pattern.compile("\\bwidth=\"(\\d+)\"")
    private val HEIGHT_ATTR = Pattern.compile("\\bheight=\"(\\d+)\"")
    private val GIPHY_PAGE =
        Pattern.compile(
            "^https?://(?:www\\.)?giphy\\.com/gifs/(?:[^/?#]*-)?([A-Za-z0-9]+)/?(?:[?#].*)?$"
        )
    private val GIPHY_MEDIA = Pattern.compile("^https?://media\\.giphy\\.com/media/([A-Za-z0-9]+)/")

    // Emote ids in comment markdown: ![img](emote|free_emotes_pack|grin),
    // ![gif](emote|t5_2r656|13103).
    private val EMOTE_MARKDOWN =
        Pattern.compile("!\\[(?:img|gif)\\]\\((emote\\|[^|)\\s]+\\|[^)\\s]+)\\)")
    private const val SNOOMOJI_PACK = "emote|free_emotes_pack|"
    private const val SNOOMOJI_URL =
        "https://www.redditstatic.com/marketplace-assets/v1/core/emotes/snoomoji_emotes/free_emotes_pack/"

    // The size of the images loaded before (URL to width and height), so an image shown again
    // starts with its final size: a redraw of the comments, after a vote, keeps their height.
    private val imageSizes = LruCache<String, IntArray>(300)

    // Images the user resized by tapping (preview URL to expanded), overriding the default.
    private val sizeOverrides = Collections.synchronizedMap(HashMap<String, Boolean>())

    // The rest is only used on the main thread.

    // Image slots added below each TextView, to remove them when the view is reused.
    private val slots = WeakHashMap<TextView, List<View>>()

    // What the slots of each TextView show, to keep them when the same content is shown again.
    private val slotKeys = WeakHashMap<TextView, String>()

    // Changes every time a TextView is bound again, so late loads for old content are dropped.
    private val generations = WeakHashMap<TextView, Any>()

    // Image slots removed because their view shows another comment now, so late loads are dropped.
    private val removedSlots = WeakHashMap<View, Boolean>()

    private val emoteLoader = Executors.newFixedThreadPool(2)

    private val settings = EmbedCommentImagesSettings

    /** Whether images are shown at full width until tapped. */
    fun fullWidthDefault(): Boolean = settings.imagesFullWidth.value

    private fun longPressDownload(): Boolean = settings.imagesLongPressDownload.value

    /**
     * Whether images and previews wait for a tap, on mobile data with "Load images only on Wi-Fi".
     */
    fun loadOnTap(context: Context): Boolean = settings.imagesWifiOnly.value && isMetered(context)

    /** Images go in slots right after the TextView, so they need a vertical LinearLayout. */
    fun embed(textView: TextView, markdown: String?) {
        // The app shows the comment again on every change (a vote, a collapsed reply...). Its
        // images are kept when they did not change: new ones would start small and make the list
        // jump.
        var previousSlots = slots.remove(textView)
        val previousKey = slotKeys.remove(textView)
        var keptSlots = false
        try {
            textView.visibility = View.VISIBLE
            val generation = Any()
            generations[textView] = generation

            val context = textView.context
            val parent =
                (textView.parent as? LinearLayout)?.takeIf {
                    it.orientation == LinearLayout.VERTICAL
                }
            val imagesOn = parent != null && settings.showImages.value
            val videosOn = parent != null && settings.showVideos.value
            val titlesOn = settings.youtubeTitles.value
            // Thumbnails are drawn inside the text.
            val thumbnailsOn = settings.youtubeThumbnails.value && !loadOnTap(context)
            val xPostsOn = settings.xPosts.value
            val blueskyPostsOn = settings.blueskyPosts.value
            if (
                !imagesOn && !videosOn && !titlesOn && !thumbnailsOn && !xPostsOn && !blueskyPostsOn
            ) {
                return
            }

            val text = fullText(textView)
            if (text !is Spanned) return

            val builder = SpannableStringBuilder(text)
            val plain = builder.toString()
            val images = ArrayList<ImageRef>()
            // Text ranges to delete, or to replace with an inline emote: start, end, and the
            // emote's index or -1.
            val edits = ArrayList<IntArray>()
            val emoteUrls = ArrayList<String>()
            val youtubeLinks = ArrayList<URLSpan>()
            val thumbnailLinks = ArrayList<URLSpan>()
            val postLinks = ArrayList<URLSpan>()
            val videoIds = ArrayList<String>()
            val albums = ArrayList<String>()

            // Links either wrap an <img> tag (Giphy comments) or point straight at an image.
            for (span in builder.getSpans(0, builder.length, URLSpan::class.java)) {
                val start = builder.getSpanStart(span)
                val end = builder.getSpanEnd(span)
                val linkText = plain.substring(start, end)
                val link = normalizeLink(span.url)
                // Only the bare link's text is dropped; words that were linked stay.
                val bare = linkText.trim().startsWith("http")
                val videoId = if (videosOn) CommentVideo.videoId(link) else null
                if (videoId != null) {
                    videoIds.add(videoId)
                    if (bare) edits.add(intArrayOf(start, end, -1))
                    continue
                }
                if (SocialPosts.postKey(link, xPostsOn, blueskyPostsOn) != null) {
                    if (SocialPosts.isBareLink(linkText)) postLinks.add(span)
                    continue
                }
                if (YoutubeTitles.isYoutubeUrl(link)) {
                    if (thumbnailsOn && YoutubeTitles.hasThumbnail(link)) thumbnailLinks.add(span)
                    if (titlesOn && YoutubeTitles.isBareYoutubeLink(linkText, span.url)) {
                        youtubeLinks.add(span)
                    }
                    continue
                }
                if (!imagesOn || link == null) continue

                if (ImgurAlbum.isAlbum(link)) {
                    albums.add(link)
                    if (bare) edits.add(intArrayOf(start, end, -1))
                    continue
                }

                val img = IMG_TAG.matcher(linkText)
                if (img.find()) {
                    // Giphy comments: the <img> is a small preview, the link is the full GIF.
                    val preview = unescape(img.group(1)!!)
                    val full = toImageUrl(link)
                    images.add(
                        ImageRef(preview, full ?: preview, width(img.group()), height(img.group()))
                    )
                    edits.add(intArrayOf(start, end, -1))
                    continue
                }

                val imageUrl = toImageUrl(link) ?: continue
                images.add(ImageRef(imageUrl, imageUrl, 0, 0))
                if (bare) edits.add(intArrayOf(start, end, -1))
            }

            // <img> tags outside links: emotes inline, anything bigger below the text.
            val img = IMG_TAG.matcher(plain)
            while (imagesOn && img.find()) {
                if (isInside(edits, img.start())) continue
                val src = unescape(img.group(1)!!)
                val width = width(img.group())
                val height = height(img.group())
                if (isEmote(width, height)) {
                    edits.add(intArrayOf(img.start(), img.end(), emoteUrls.size))
                    emoteUrls.add(src)
                } else {
                    images.add(ImageRef(src, src, width, height))
                    edits.add(intArrayOf(img.start(), img.end(), -1))
                }
            }

            val emoteIds = emoteIds(markdown)
            if (emoteIds.size == emoteUrls.size) {
                for (i in emoteUrls.indices) emoteUrls[i] = emoteUrl(emoteIds[i], emoteUrls[i])
            }

            if (
                edits.isEmpty() &&
                    images.isEmpty() &&
                    albums.isEmpty() &&
                    videoIds.isEmpty() &&
                    youtubeLinks.isEmpty() &&
                    thumbnailLinks.isEmpty() &&
                    postLinks.isEmpty()
            ) {
                return
            }

            // From the end, so earlier offsets stay valid.
            edits.sortByDescending { it[0] }
            val emoteSize = Math.round(textView.textSize * 1.3f)
            val emoteSpans = ArrayList<Pair<ImageSpan, String>>()
            for (edit in edits) {
                if (edit[2] < 0) {
                    builder.delete(edit[0], edit[1])
                } else {
                    builder.replace(edit[0], edit[1], "\uFFFC")
                    val empty = ColorDrawable(Color.TRANSPARENT)
                    empty.setBounds(0, 0, emoteSize, emoteSize)
                    val placeholder = ImageSpan(empty, ImageSpan.ALIGN_BOTTOM)
                    builder.setSpan(
                        placeholder,
                        edit[0],
                        edit[0] + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    emoteSpans.add(placeholder to emoteUrls[edit[2]])
                }
            }
            val pendingTitles = YoutubeTitles.prepare(textView, builder, youtubeLinks)
            // The photos of X and Bluesky posts wait like images where they load only on Wi-Fi.
            val postPhotosOn = !loadOnTap(context)
            val postImages = ArrayList<SocialPosts.Image>()
            val pendingPosts =
                SocialPosts.prepare(textView, builder, postLinks, postPhotosOn, postImages)
            val thumbnails =
                YoutubeTitles.insertThumbnails(
                    textView,
                    builder,
                    thumbnailLinks,
                    fullWidthDefault(),
                )
            trimWhitespace(builder)

            if (builder.isEmpty()) {
                textView.visibility = View.GONE
            } else {
                textView.setText(builder, TextView.BufferType.SPANNABLE)
            }

            YoutubeTitles.load(textView, generation, pendingTitles)
            SocialPosts.load(textView, generation, pendingPosts, postPhotosOn)
            SocialPosts.loadImages(textView, generation, postImages)
            YoutubeTitles.loadThumbnails(textView, generation, thumbnails)
            for ((placeholder, url) in emoteSpans) {
                loadEmote(textView, generation, placeholder, url, emoteSize)
            }

            if (parent == null || (images.isEmpty() && albums.isEmpty() && videoIds.isEmpty())) {
                return
            }
            val key = slotKey(images, albums, videoIds)
            if (key == previousKey && isAttached(parent, previousSlots)) {
                slots[textView] = previousSlots
                slotKeys[textView] = key
                keptSlots = true
                return
            }
            removeSlots(previousSlots)
            previousSlots = null
            val added = ArrayList<View>()
            var index = parent.indexOfChild(textView) + 1
            for (image in images) {
                val slot = createImageSlot(textView, image)
                parent.addView(slot, index++)
                added.add(slot)
            }
            for (album in albums) {
                val slot = ImgurAlbum.createSlot(textView, album)
                parent.addView(slot, index++)
                added.add(slot)
            }
            val fullWidth = fullWidthDefault()
            val maxHeight =
                (textView.resources.displayMetrics.heightPixels * EXPANDED_MAX_SCREEN_HEIGHT)
                    .toInt()
            val loadPreviews = !loadOnTap(context)
            for (videoId in videoIds) {
                val slot =
                    CommentVideo.createSlot(
                        textView,
                        videoId,
                        fullWidth,
                        dp(context, IMAGE_SIZE_DP),
                        maxHeight,
                        loadPreviews,
                    )
                parent.addView(slot, index++)
                added.add(slot)
            }
            slots[textView] = added
            slotKeys[textView] = key
        } catch (throwable: Throwable) {
            ErrorDialog.report(TAG, "Could not show the images of the text", throwable)
        } finally {
            if (!keptSlots) removeSlots(previousSlots)
        }
    }

    /** Identifies the images, albums and videos shown under a text. */
    private fun slotKey(images: List<ImageRef>, albums: List<String>, videoIds: List<String>) =
        buildString {
            for (image in images) {
                append("i:").append(image.previewUrl).append('|').append(image.fullUrl).append('\n')
            }
            for (album in albums) append("a:").append(album).append('\n')
            for (videoId in videoIds) append("v:").append(videoId).append('\n')
        }

    /** Whether the slots are still shown under the text. */
    private fun isAttached(parent: LinearLayout, slots: List<View>?): Boolean {
        if (slots.isNullOrEmpty()) return false
        return slots.all { it.parent === parent && !removedSlots.containsKey(it) }
    }

    /**
     * The whole text of the view. The text of image, GIF and video posts is in an
     * ExpandableTextView, which shows a shortened copy of it until "see more" is tapped.
     */
    fun fullText(textView: TextView): CharSequence? {
        if (textView is ExpandableTextView) {
            try {
                textView.originalText?.let {
                    return it
                }
            } catch (exception: Exception) {
                Log.i(TAG, "Could not get the whole text", exception)
            }
        }
        return textView.text
    }

    /** After the whole text was changed in place: an ExpandableTextView makes its copy again. */
    fun fullTextChanged(textView: TextView, text: CharSequence) {
        if (textView is ExpandableTextView) textView.setText(text, TextView.BufferType.SPANNABLE)
    }

    /** Whether the TextView still shows the content a late load was started for. */
    fun isCurrent(textView: TextView, generation: Any): Boolean =
        generations[textView] === generation

    /** The view is reused for another comment: drops the images added for the previous one. */
    private fun removeSlots(old: List<View>?) {
        if (old == null) return
        for (slot in old) {
            CommentVideo.release(slot)
            ImgurAlbum.release(slot)
            discardSlot(slot)
            (slot.parent as? ViewGroup)?.removeView(slot)
        }
    }

    /** Stops loading the image of a slot that is not shown anymore. */
    fun discardSlot(slot: View) {
        removedSlots[slot] = true
        (slot as? ViewGroup)?.getChildAt(0)?.let { GlideUtils.clear(it) }
    }

    /**
     * A slot shows a placeholder until the image loads, then the image replaces it. When Reddit
     * gives the image size, the placeholder has the exact final size, so the list does not jump;
     * otherwise it is two text lines tall. Its colors come from the text color, so it matches the
     * light, dark and black themes.
     */
    fun createImageSlot(textView: TextView, found: ImageRef): View {
        val loadedSize =
            if (found.width > 0 && found.height > 0) null else imageSizes.get(found.fullUrl)
        val image =
            if (loadedSize == null) found
            else ImageRef(found.previewUrl, found.fullUrl, loadedSize[0], loadedSize[1])
        val context = textView.context
        val textColor = textView.currentTextColor
        val faintColor = withAlpha(textColor, 0x1A)
        val mutedColor = withAlpha(textColor, 0x80)
        val collapsedSize = dp(context, IMAGE_SIZE_DP)
        val expandedMaxHeight =
            (textView.resources.displayMetrics.heightPixels * EXPANDED_MAX_SCREEN_HEIGHT).toInt()
        val lineBoxHeight = Math.max(textView.lineHeight * PLACEHOLDER_LINES, dp(context, 32))

        val slot = FrameLayout(context)
        slot.layoutParams =
            LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                .apply { topMargin = dp(context, 4) }

        // The image starts empty (0x0) and grows when Glide sets it.
        val imageView = ImageView(context)
        imageView.id = viewId(context, image.fullUrl)
        imageView.adjustViewBounds = true
        imageView.scaleType = ImageView.ScaleType.FIT_START
        slot.addView(
            imageView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val knownSize = image.width > 0 && image.height > 0
        val placeholder =
            if (knownSize) {
                AspectRatioFrameLayout(
                    context,
                    image.height.toFloat() / image.width,
                    expandedMaxHeight,
                )
            } else {
                FrameLayout(context)
            }
        placeholder.background =
            GradientDrawable().apply {
                setColor(faintColor)
                cornerRadius = dp(context, 6).toFloat()
            }
        slot.addView(placeholder, FrameLayout.LayoutParams(collapsedSize, lineBoxHeight))

        val message = TextView(context)
        message.setTextColor(mutedColor)
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        message.visibility = View.GONE
        placeholder.addView(
            message,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )

        // The download progress, at the bottom of the placeholder, or of the image while the full
        // image loads.
        val progress = DownloadProgress(context)
        slot.addView(
            progress.root,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ),
        )

        var expanded = isExpanded(image)
        var fullLoaded = false
        var loadStarted = false
        val applySize = {
            applySize(
                slot,
                imageView,
                placeholder,
                image,
                expanded,
                knownSize,
                collapsedSize,
                expandedMaxHeight,
                lineBoxHeight,
            )
        }
        applySize()

        val firstLoad =
            object : GlideUtils.LoadCallback {
                override fun onReady() {
                    placeholder.visibility = View.GONE
                    val drawable = imageView.drawable
                    if (
                        drawable != null &&
                            drawable.intrinsicWidth > 0 &&
                            drawable.intrinsicHeight > 0
                    ) {
                        imageSizes.put(
                            image.fullUrl,
                            intArrayOf(drawable.intrinsicWidth, drawable.intrinsicHeight),
                        )
                    }
                }

                override fun onFailed() {
                    message.text = BaconReaderStrings.embedImageUnavailable
                    message.visibility = View.VISIBLE
                    progress.fail()
                }
            }
        val startLoad = {
            if (!loadStarted) {
                loadStarted = true
                message.visibility = View.GONE
                val url = if (expanded) image.fullUrl else image.previewUrl
                loadWithProgress(slot, imageView, progress, url, null, firstLoad)
                fullLoaded = expanded || image.fullUrl == image.previewUrl
            }
        }

        if (loadOnTap(context)) {
            message.text = BaconReaderStrings.embedImageTapToLoad
            message.visibility = View.VISIBLE
            placeholder.setOnClickListener { startLoad() }
        } else {
            startLoad()
        }

        val gestures =
            GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent): Boolean = true

                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        expanded = !expanded
                        sizeOverrides[image.previewUrl] = expanded
                        applySize()
                        // Swaps the small preview for the full image. The preview is loaded as
                        // Glide's thumbnail, so it stays on screen until the full one is ready.
                        // The current Drawable can't be the placeholder: Glide may recycle its
                        // bitmap.
                        if (expanded && !fullLoaded) {
                            fullLoaded = true
                            loadWithProgress(
                                slot,
                                imageView,
                                progress,
                                image.fullUrl,
                                image.previewUrl,
                                null,
                            )
                        }
                        return true
                    }

                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        showViewer(context, image)
                        return true
                    }

                    override fun onLongPress(e: MotionEvent) {
                        if (!longPressDownload()) return
                        imageView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        download(context, image.fullUrl)
                    }
                },
            )
        imageView.setOnTouchListener { _, event -> gestures.onTouchEvent(event) }
        return slot
    }

    /** Gives an image the size the user chose for another one, unless it has its own. */
    fun inheritSize(from: ImageRef, to: ImageRef) {
        val expanded = sizeOverrides[from.previewUrl] ?: return
        if (!sizeOverrides.containsKey(to.previewUrl)) sizeOverrides[to.previewUrl] = expanded
    }

    private fun isExpanded(image: ImageRef): Boolean =
        sizeOverrides[image.previewUrl] ?: fullWidthDefault()

    /**
     * Collapsed: fits a 200x200dp box. Expanded: full width, keeping the aspect ratio, at most 80%
     * of the screen height. The placeholder gets the size the image will have.
     */
    private fun applySize(
        slot: FrameLayout,
        imageView: ImageView,
        placeholder: FrameLayout,
        image: ImageRef,
        expanded: Boolean,
        knownSize: Boolean,
        collapsedSize: Int,
        expandedMaxHeight: Int,
        lineBoxHeight: Int,
    ) {
        val width =
            if (expanded) ViewGroup.LayoutParams.MATCH_PARENT
            else ViewGroup.LayoutParams.WRAP_CONTENT
        slot.layoutParams.width = width
        imageView.layoutParams.width = width
        imageView.maxWidth = if (expanded) Int.MAX_VALUE else collapsedSize
        imageView.maxHeight = if (expanded) expandedMaxHeight else collapsedSize

        val placeholderParams = placeholder.layoutParams
        if (expanded) {
            placeholderParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            placeholderParams.height =
                if (knownSize) ViewGroup.LayoutParams.WRAP_CONTENT else lineBoxHeight
        } else if (knownSize) {
            // Like the image: scaled down to fit the box, never up.
            val box =
                if (image.width <= collapsedSize && image.height <= collapsedSize) {
                    intArrayOf(image.width, image.height)
                } else {
                    fitInBox(image.width, image.height, collapsedSize)
                }
            placeholderParams.width = box[0]
            placeholderParams.height = box[1]
        } else {
            placeholderParams.width = collapsedSize
            placeholderParams.height = lineBoxHeight
        }
        slot.requestLayout()
    }

    /** The size of a width x height image scaled to fit a box x box square. */
    private fun fitInBox(width: Int, height: Int, box: Int): IntArray {
        val scale = Math.min(box.toFloat() / width, box.toFloat() / height)
        return intArrayOf(
            Math.max(1, Math.round(width * scale)),
            Math.max(1, Math.round(height * scale)),
        )
    }

    private fun isMetered(context: Context): Boolean =
        try {
            val manager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            manager != null && manager.isActiveNetworkMetered
        } catch (throwable: Throwable) {
            false
        }

    /**
     * Downloads the image with a progress bar, then shows it. When the download fails, Glide loads
     * it instead. [thumbnailUrl] is an image shown until the image is loaded, or null.
     */
    private fun loadWithProgress(
        slot: FrameLayout,
        imageView: ImageView,
        progress: DownloadProgress,
        url: String,
        thumbnailUrl: String?,
        callback: GlideUtils.LoadCallback?,
    ) {
        progress.start()
        ImageDownloader.download(
            slot.context,
            url,
            object : ImageDownloader.Listener {
                override fun onProgress(received: Long, total: Long) {
                    progress.update(received, total)
                }

                override fun onDownloaded(file: File) {
                    progress.stop()
                    // The view shows another comment now.
                    if (removedSlots.containsKey(slot)) return
                    try {
                        GlideUtils.load(
                            imageView,
                            file,
                            thumbnailUrl,
                            object : GlideUtils.LoadCallback {
                                override fun onReady() {
                                    callback?.onReady()
                                }

                                override fun onFailed() {
                                    // A damaged download: forget it and let Glide load the image.
                                    file.delete()
                                    loadUrl()
                                }
                            },
                        )
                    } catch (exception: Exception) {
                        ErrorDialog.report(TAG, "Could not show the image", exception)
                    }
                }

                override fun onFailed() {
                    progress.stop()
                    loadUrl()
                }

                private fun loadUrl() {
                    if (removedSlots.containsKey(slot)) return
                    try {
                        GlideUtils.load(imageView, url, thumbnailUrl, callback)
                    } catch (exception: Exception) {
                        ErrorDialog.report(TAG, "Could not load the image", exception)
                    }
                }
            },
        )
    }

    /**
     * A full-screen black viewer: pinch to zoom, drag, double tap to zoom, tap or back to close.
     */
    private fun showViewer(context: Context, image: ImageRef) {
        try {
            val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            val root = FrameLayout(context)
            root.setBackgroundColor(Color.BLACK)

            val zoomView = ZoomImageView(context)
            root.addView(
                zoomView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )

            val spinner = ProgressBar(context)
            spinner.indeterminateTintList = ColorStateList.valueOf(0x99FFFFFF.toInt())
            root.addView(
                spinner,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )

            zoomView.onTap = Runnable { dialog.dismiss() }
            zoomView.onLongPress = Runnable {
                if (longPressDownload()) download(context, image.fullUrl)
            }
            dialog.setContentView(root)
            dialog.show()

            val downloaded = ImageDownloader.cached(context, image.fullUrl)
            if (downloaded != null) {
                GlideUtils.load(zoomView, downloaded, null, viewerCallback(context, spinner))
                return
            }
            GlideUtils.load(
                zoomView,
                image.fullUrl,
                if (image.fullUrl == image.previewUrl) null else image.previewUrl,
                viewerCallback(context, spinner),
            )
        } catch (throwable: Throwable) {
            ErrorDialog.report(TAG, "Could not open the image viewer", throwable)
        }
    }

    private fun viewerCallback(context: Context, spinner: ProgressBar) =
        object : GlideUtils.LoadCallback {
            override fun onReady() {
                spinner.visibility = View.GONE
            }

            override fun onFailed() {
                spinner.visibility = View.GONE
                Toast.makeText(
                        context,
                        BaconReaderStrings.embedImageUnavailable,
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            }
        }

    /** Loads a small image off the main thread and swaps it into the text for the placeholder. */
    private fun loadEmote(
        textView: TextView,
        generation: Any,
        placeholder: ImageSpan,
        url: String,
        size: Int,
    ) {
        emoteLoader.execute {
            try {
                val drawable = GlideUtils.fetch(textView, url, size, size)
                textView.post { replaceEmote(textView, generation, placeholder, drawable, size) }
            } catch (exception: Exception) {
                Log.i(TAG, "Could not load the emote $url", exception)
            }
        }
    }

    private fun replaceEmote(
        textView: TextView,
        generation: Any,
        placeholder: ImageSpan,
        drawable: Drawable,
        size: Int,
    ) {
        // The view shows another comment now.
        if (!isCurrent(textView, generation)) return
        val text = fullText(textView) as? Spannable ?: return
        val start = text.getSpanStart(placeholder)
        val end = text.getSpanEnd(placeholder)
        if (start < 0) return
        val width =
            if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                Math.round(size * drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight)
            } else {
                size
            }
        drawable.setBounds(0, 0, width, size)
        text.removeSpan(placeholder)
        text.setSpan(
            ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        fullTextChanged(textView, text)
    }

    /** Emote ids in the order they appear in the markdown. */
    private fun emoteIds(markdown: String?): List<String> {
        val ids = ArrayList<String>()
        if (markdown == null) return ids
        val matcher = EMOTE_MARKDOWN.matcher(markdown)
        while (matcher.find()) ids.add(matcher.group(1)!!)
        return ids
    }

    /**
     * The image of a Reddit snoomoji (free emotes pack), whose URL follows from its name. Subreddit
     * emotes can't be derived, so they keep the URL Reddit sent.
     */
    private fun emoteUrl(id: String, fallback: String): String {
        if (!id.startsWith(SNOOMOJI_PACK)) return fallback
        val name = id.substring(SNOOMOJI_PACK.length)
        if (!name.matches(Regex("[A-Za-z0-9_]+"))) return fallback
        return "$SNOOMOJI_URL$name.gif"
    }

    private fun isEmote(width: Int, height: Int): Boolean =
        width > 0 && height > 0 && width <= EMOTE_MAX_SIZE && height <= EMOTE_MAX_SIZE

    /** Saves the full-size image to Downloads/BaconReader with the system's DownloadManager. */
    private fun download(context: Context, url: String) {
        try {
            val fileName = fileNameFor(url)
            val request = DownloadManager.Request(Uri.parse(url))
            request.setTitle(fileName)
            request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "BaconReader/$fileName",
            )
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(context, BaconReaderStrings.embedImageDownloading, Toast.LENGTH_SHORT)
                .show()
        } catch (throwable: Throwable) {
            ErrorDialog.report(TAG, "Could not download the image", throwable)
            Toast.makeText(
                    context,
                    BaconReaderStrings.embedImageDownloadFailed,
                    Toast.LENGTH_SHORT,
                )
                .show()
        }
    }

    /** A readable file name for an image URL: "giphy-<id>.gif", or the URL's last path segment. */
    private fun fileNameFor(url: String): String {
        val giphy = GIPHY_MEDIA.matcher(url)
        if (giphy.find()) return "giphy-" + giphy.group(1) + ".gif"

        val path = url.replaceFirst(Regex("[?#].*$"), "")
        var name = path.substring(path.lastIndexOf('/') + 1).replace(Regex("[^A-Za-z0-9._-]"), "_")
        if (name.isEmpty()) name = "image"
        if (!name.contains(".")) name += ".jpg"
        return name
    }

    /** Undoes the app's internal browser rewrite: "browser-activity-https://x" to "https://x". */
    fun normalizeLink(url: String?): String? {
        if (url == null) return null
        if (url.startsWith("browser-activity-https:")) {
            return "https:" + url.substring("browser-activity-https:".length)
        }
        if (url.startsWith("browser-activity:")) {
            return "http:" + url.substring("browser-activity:".length)
        }
        return url
    }

    /** A URL that loads the image of a link directly, or null when the link is not an image. */
    private fun toImageUrl(url: String?): String? {
        if (url == null || !url.startsWith("http")) return null

        val giphy = GIPHY_PAGE.matcher(url)
        if (giphy.matches()) return "https://media.giphy.com/media/" + giphy.group(1) + "/giphy.gif"

        val lower = url.lowercase(Locale.ROOT)
        val path = lower.replaceFirst(Regex("[?#].*$"), "")
        if (path.endsWith(".gifv") && path.contains("imgur.com/")) {
            return url.replaceFirst(Regex("(?i)\\.gifv"), ".gif")
        }
        if (
            path.endsWith(".gif") ||
                path.endsWith(".png") ||
                path.endsWith(".jpg") ||
                path.endsWith(".jpeg") ||
                path.endsWith(".webp")
        ) {
            return url
        }
        if (
            lower.startsWith("https://i.redd.it/") || lower.startsWith("https://preview.redd.it/")
        ) {
            return url
        }
        return null
    }

    private fun unescape(url: String): String = url.replace("&amp;", "&")

    /**
     * The id of an image's view, a GIF's apart from an image's (the patch adds both to the app's
     * resources): the e2e tests find the images by it.
     */
    private fun viewId(context: Context, url: String): Int {
        val path = url.lowercase(Locale.ROOT).replaceFirst(Regex("[?#].*$"), "")
        val name =
            if (path.endsWith(".gif")) "baconreader_patches_comment_gif"
            else "baconreader_patches_comment_image"
        val id = context.resources.getIdentifier(name, "id", context.packageName)
        return if (id == 0) View.NO_ID else id
    }

    /** The width attribute of an <img> tag, or 0. */
    private fun width(tag: String): Int = intAttr(WIDTH_ATTR, tag)

    /** The height attribute of an <img> tag, or 0. */
    private fun height(tag: String): Int = intAttr(HEIGHT_ATTR, tag)

    private fun intAttr(pattern: Pattern, tag: String): Int {
        val matcher = pattern.matcher(tag)
        if (!matcher.find()) return 0
        return matcher.group(1)?.toIntOrNull() ?: 0
    }

    private fun isInside(ranges: List<IntArray>, index: Int): Boolean = ranges.any {
        index >= it[0] && index < it[1]
    }

    /** Removes the empty lines that removed links leave before and after the text. */
    private fun trimWhitespace(builder: SpannableStringBuilder) {
        var end = builder.length
        while (end > 0 && isBlank(builder[end - 1])) end--
        builder.delete(end, builder.length)
        var start = 0
        while (start < builder.length && isBlank(builder[start])) start++
        builder.delete(0, start)
    }

    /** Whitespace, including the invisible characters Reddit's editor leaves in comments. */
    private fun isBlank(c: Char): Boolean =
        Character.isWhitespace(c) ||
            c == '\u00A0' ||
            c == '\u200B' ||
            c == '\u200C' ||
            c == '\u200D' ||
            c == '\uFEFF'

    fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    fun dp(context: Context, value: Int): Int =
        Math.round(
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value.toFloat(),
                context.resources.displayMetrics,
            )
        )
}
