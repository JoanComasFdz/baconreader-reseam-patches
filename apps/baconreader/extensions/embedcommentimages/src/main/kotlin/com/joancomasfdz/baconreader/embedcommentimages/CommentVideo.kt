// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.embedcommentimages

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import android.util.TypedValue
import android.util.Xml
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.MediaSourceFactory
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.ui.PlayerControlView
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.joancomasfdz.baconreader.common.ErrorDialog
import com.joancomasfdz.baconreader.common.GlideUtils
import com.joancomasfdz.baconreader.embedcommentimages.CommentImages.dp
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import java.io.IOException
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import org.xmlpull.v1.XmlPullParser

/**
 * Videos uploaded in comments, linked as reddit.com/link/[post]/video/[id]/player, and links to
 * video files (.mp4, .webm). Reddit's web page for the first doesn't work, so they are played in
 * the comment, with the ExoPlayer bundled in BaconReader.
 */
internal object CommentVideo {
    private const val TAG = "CommentVideo"
    private const val DEFAULT_RATIO = 9f / 16f
    private const val PLAYER_USER_AGENT = "BaconReader"
    private const val BUTTON_SIZE_DP = 36
    private const val ORIENTATION_RELEASE_DELAY_MS = 1000L

    // PlayerView.setShowBuffering: show the spinner while buffering when playing.
    private const val SHOW_BUFFERING_WHEN_PLAYING = 1

    // Player.STATE_ENDED: the video played to its end.
    private const val STATE_ENDED = 4

    private val loader = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    // The size, duration and first frame of the last videos shown.
    private val infos = LruCache<String, Info>(20)

    // The video playing in a comment. Only one plays at a time.
    private var currentPlayer: SimpleExoPlayer? = null

    // The full screen showing the playing video, if any.
    private var fullScreen: Dialog? = null

    /**
     * What identifies the video of a link: the id of a comment video, the link itself for a video
     * file, or null.
     */
    fun videoId(url: String?): String? {
        if (url == null) return null
        val uri = Uri.parse(url)
        val scheme = uri.scheme
        var host = uri.host
        if (
            host == null ||
                !("http".equals(scheme, ignoreCase = true) ||
                    "https".equals(scheme, ignoreCase = true))
        ) {
            return null
        }
        host = host.lowercase(Locale.ROOT)

        // reddit.com/link/[post]/video/[id]/player
        val segments = uri.pathSegments
        if (
            (host == "reddit.com" || host.endsWith(".reddit.com")) &&
                segments.size == 5 &&
                segments[0] == "link" &&
                segments[2] == "video" &&
                segments[4] == "player"
        ) {
            return segments[3]
        }

        val path = uri.path?.lowercase(Locale.ROOT) ?: return null
        return if (path.endsWith(".mp4") || path.endsWith(".webm")) url else null
    }

    /** A video file link, played as it is, rather than a comment video (a DASH playlist). */
    private fun isFile(id: String): Boolean = id.startsWith("http")

    private fun videoUrl(id: String): String = "https://v.redd.it/$id"

    /**
     * A box with the first frame of the video and a play button. With [fullWidth], the box is as
     * wide as the comment, else at most [boxSize] wide and high. Tapping it plays the video in
     * place, as wide as the comment and at most [maxHeight] high: at [boxSize], the player's
     * controls cover most of the video. While the controls show, two buttons show above them: one
     * switches between the two sizes, the other opens the video in full screen. [loadPreview] is
     * whether the first frame can load now, not where images load on tap: there the box still takes
     * the video's size and duration from its playlist.
     */
    fun createSlot(
        textView: TextView,
        id: String,
        fullWidth: Boolean,
        boxSize: Int,
        maxHeight: Int,
        loadPreview: Boolean,
    ): View {
        val context = textView.context
        val slot =
            object : FrameLayout(context) {
                override fun onWindowVisibilityChanged(visibility: Int) {
                    super.onWindowVisibilityChanged(visibility)
                    // The app went to the background, or another screen is on top.
                    if (visibility != View.VISIBLE) pauseVideo(this)
                }
            }
        slot.layoutParams =
            CommentImages.slotLayoutParams(
                textView,
                if (fullWidth) ViewGroup.LayoutParams.MATCH_PARENT else boxSize,
            )
        slot.tag = CommentVideo::class.java

        val box =
            AspectRatioFrameLayout(context, DEFAULT_RATIO, if (fullWidth) maxHeight else boxSize)
        box.id = CommentImages.resourceId(context, "baconreader_patches_comment_video")
        box.background =
            GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = dp(context, 6).toFloat()
            }
        box.clipToOutline = true
        slot.addView(
            box,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val preview = ImageView(context)
        preview.scaleType = ImageView.ScaleType.FIT_CENTER
        box.addView(
            preview,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        val play = ImageView(context)
        play.setImageDrawable(YoutubeTitles.PlayDrawable(dp(context, 48)))
        box.addView(
            play,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )

        val duration = TextView(context)
        duration.setTextColor(Color.WHITE)
        duration.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        val padH = dp(context, 6)
        val padV = dp(context, 2)
        duration.setPadding(padH, padV, padH, padV)
        duration.background =
            GradientDrawable().apply {
                setColor(0x99000000.toInt())
                cornerRadius = dp(context, 8).toFloat()
            }
        duration.visibility = View.GONE
        box.addView(
            duration,
            FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.START,
                )
                .apply { setMargins(dp(context, 6), 0, 0, dp(context, 6)) },
        )

        if (loadPreview) loadInfo(box, preview, duration, id)
        else loadSize(box, preview, duration, id)

        box.setOnClickListener {
            val playerView = startPlayer(box, id) ?: return@setOnClickListener
            play.visibility = View.GONE
            duration.visibility = View.GONE
            box.setOnClickListener(null)
            addButtons(slot, box, playerView, boxSize, maxHeight)
        }

        // A player keeps playing and holds its decoder until released.
        slot.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}

                override fun onViewDetachedFromWindow(v: View) {
                    release(slot)
                }
            }
        )
        return slot
    }

    private fun pauseVideo(slot: FrameLayout) {
        val player = slot.getChildAt(0)?.tag as? SimpleExoPlayer ?: return
        pause(player)
    }

    /** Stops the video of the slot, when it plays. */
    fun release(slot: View) {
        if (slot !is FrameLayout || slot.tag !== CommentVideo::class.java) return
        val box = slot.getChildAt(0) ?: return
        val player = box.tag as? SimpleExoPlayer ?: return
        box.tag = null
        if (currentPlayer === player) closeFullScreen()
        releasePlayer(player)
    }

    /**
     * The buttons over a playing video, shown with its controls: one switches between full width
     * and [boxSize], the other opens the full screen. The video starts at full width.
     */
    private fun addButtons(
        slot: FrameLayout,
        box: AspectRatioFrameLayout,
        playerView: PlayerView,
        boxSize: Int,
        maxHeight: Int,
    ) {
        val context = box.context
        var expanded = true
        val size = button(context)
        val sizeIcon = ButtonDrawable(ButtonDrawable.SHRINK)
        size.setImageDrawable(sizeIcon)

        fun applySize() {
            slot.layoutParams.width = if (expanded) ViewGroup.LayoutParams.MATCH_PARENT else boxSize
            slot.requestLayout()
            box.maxHeight = if (expanded) maxHeight else boxSize
            sizeIcon.kind = if (expanded) ButtonDrawable.SHRINK else ButtonDrawable.ENLARGE
            size.contentDescription =
                if (expanded) BaconReaderStrings.embedVideoShrink
                else BaconReaderStrings.embedVideoEnlarge
        }
        applySize()
        size.setOnClickListener {
            expanded = !expanded
            applySize()
        }

        val full = button(context)
        full.setImageDrawable(ButtonDrawable(ButtonDrawable.FULL_SCREEN))
        full.contentDescription = BaconReaderStrings.embedVideoFullScreen
        full.setOnClickListener { openFullScreen(box, playerView) }

        val buttons = buttonRow(context, size, full)
        box.addView(buttons, buttonRowParams(context))
        followControls(playerView, buttons)
    }

    /**
     * Shows the playing video of [box] over the whole screen, turned to landscape when the video is
     * wider than high. Back or the button closes it, and the video plays on in the comment.
     */
    private fun openFullScreen(box: AspectRatioFrameLayout, inline: PlayerView) {
        try {
            val player = box.tag as? SimpleExoPlayer ?: return
            val activity = activityOf(box.context) ?: return
            closeFullScreen()

            val root = FrameLayout(activity)
            root.setBackgroundColor(Color.BLACK)
            val playerView = newPlayerView(activity)
            playerView.id =
                CommentImages.resourceId(activity, "baconreader_patches_video_full_screen")
            root.addView(
                playerView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            val exit = button(activity)
            exit.setImageDrawable(ButtonDrawable(ButtonDrawable.EXIT_FULL_SCREEN))
            exit.contentDescription = BaconReaderStrings.embedVideoExitFullScreen
            val buttons = buttonRow(activity, exit)
            root.addView(buttons, buttonRowParams(activity))

            val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            dialog.setContentView(root)
            exit.setOnClickListener { dialog.dismiss() }

            val orientation = activity.requestedOrientation
            val turned = box.ratio < 1f
            val before =
                if (
                    activity.resources.configuration.orientation ==
                        Configuration.ORIENTATION_LANDSCAPE
                ) {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            if (turned) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            dialog.setOnDismissListener {
                if (fullScreen === dialog) fullScreen = null
                if (turned) {
                    // Given back right away, the app's own orientation follows the sensor, which
                    // keeps landscape while the phone lies flat. The screen turns back first.
                    activity.requestedOrientation = before
                    main.postDelayed(
                        { if (fullScreen == null) activity.requestedOrientation = orientation },
                        ORIENTATION_RELEASE_DELAY_MS,
                    )
                }
                if (box.tag === player) PlayerView.switchTargetView(player, playerView, inline)
            }

            // A new view shows no frame of a video that has ended: it starts over, paused. The
            // player still wants to play once it has ended, so it's paused first.
            if (player.playbackState == STATE_ENDED) {
                player.setPlayWhenReady(false)
                player.seekTo(0, 0L)
            }
            PlayerView.switchTargetView(player, inline, playerView)
            followControls(playerView, buttons)
            dialog.show()
            // The theme hides the status bar. Hiding the navigation bar too would show Android's
            // "Viewing full screen" message over the video, the first time.
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            fullScreen = dialog
        } catch (exception: Exception) {
            ErrorDialog.report(TAG, "Could not show the video in full screen", exception)
        }
    }

    private fun closeFullScreen() {
        val dialog = fullScreen ?: return
        fullScreen = null
        try {
            dialog.dismiss()
        } catch (exception: Exception) {
            Log.i(TAG, "Could not close the full screen", exception)
        }
    }

    private fun activityOf(context: Context): Activity? {
        var current: Context? = context
        while (current != null) {
            if (current is Activity) return current
            current = (current as? ContextWrapper)?.baseContext
        }
        return null
    }

    /** Shows [buttons] while the player's controls show, and hides them with the controls. */
    private fun followControls(playerView: PlayerView, buttons: View) {
        buttons.visibility = if (playerView.isControllerVisible) View.VISIBLE else View.GONE
        playerView.setControllerVisibilityListener(
            PlayerControlView.VisibilityListener { visibility -> buttons.visibility = visibility }
        )
    }

    private fun button(context: Context): ImageView {
        val button = ImageView(context)
        button.scaleType = ImageView.ScaleType.FIT_CENTER
        return button
    }

    private fun buttonRow(context: Context, vararg buttons: ImageView): LinearLayout {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        val size = dp(context, BUTTON_SIZE_DP)
        for ((index, button) in buttons.withIndex()) {
            row.addView(
                button,
                LinearLayout.LayoutParams(size, size).apply {
                    if (index > 0) leftMargin = dp(context, 8)
                },
            )
        }
        return row
    }

    private fun buttonRowParams(context: Context): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            )
            .apply {
                val margin = dp(context, 6)
                setMargins(margin, margin, margin, margin)
            }

    /**
     * The size and duration from the video's DASH playlist, a small text, without the first frame,
     * where images load on tap: the box has the video's shape before the tap, so it doesn't change
     * size while the video plays. A video file has no playlist: the player fits it in the box.
     */
    private fun loadSize(
        box: AspectRatioFrameLayout,
        preview: ImageView,
        duration: TextView,
        id: String,
    ) {
        val cached = infos.get(id)
        if (cached != null) {
            showInfo(cached, box, preview, duration)
            return
        }
        if (isFile(id)) return

        loader.execute {
            try {
                val playlist =
                    Playlist.parse(fetchTextWithRetry(videoUrl(id) + "/DASHPlaylist.mpd"))
                val info = Info(playlist.size, playlist.seconds)
                infos.put(id, info)
                main.post { showInfo(info, box, preview, duration) }
            } catch (exception: Exception) {
                Log.i(TAG, "Could not load the video size of $id", exception)
            }
        }
    }

    /**
     * The size and duration from the video's DASH playlist (or from the file), and its first frame.
     */
    private fun loadInfo(
        box: AspectRatioFrameLayout,
        preview: ImageView,
        duration: TextView,
        id: String,
    ) {
        // The comment is drawn again when scrolled back: what is known shows right away.
        val cached = infos.get(id)
        if (cached != null) {
            showInfo(cached, box, preview, duration)
            if (cached.frame != null) return
        }

        loader.execute {
            if (isFile(id)) {
                loadFileInfo(box, preview, duration, id, id, -1)
                return@execute
            }
            try {
                // Every rendition has the same ratio and length: they are read from the smallest.
                val playlist =
                    Playlist.parse(fetchTextWithRetry(videoUrl(id) + "/DASHPlaylist.mpd"))
                loadFileInfo(
                    box,
                    preview,
                    duration,
                    videoUrl(id) + "/" + playlist.smallest,
                    id,
                    playlist.seconds,
                )
            } catch (exception: Exception) {
                Log.i(TAG, "Could not load the video information of $id", exception)
            }
        }
    }

    /**
     * The size, duration and first frame of a video file, read from the file and kept under [key].
     * [knownSeconds] is the duration to use when the file doesn't say it, or -1. Blocks.
     */
    private fun loadFileInfo(
        box: AspectRatioFrameLayout,
        preview: ImageView,
        duration: TextView,
        url: String,
        key: String,
        knownSeconds: Long,
    ) {
        for (attempt in 1..GlideUtils.MAX_ATTEMPTS) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(url, HashMap())
                var width =
                    parseInt(
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    )
                var height =
                    parseInt(
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    )
                val rotation =
                    parseInt(
                        retriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
                        )
                    )
                if (rotation == 90 || rotation == 270) {
                    val swap = width
                    width = height
                    height = swap
                }
                val millis =
                    parseInt(
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        )
                        .toLong()
                val info =
                    Info(
                        if (width > 0 && height > 0) intArrayOf(width, height) else null,
                        if (millis > 0) Math.round(millis / 1000.0) else knownSeconds,
                    )
                info.frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                infos.put(key, info)
                main.post { showInfo(info, box, preview, duration) }
                return
            } catch (exception: Exception) {
                Log.i(TAG, "Could not load the video information of $url", exception)
            } finally {
                try {
                    retriever.release()
                } catch (exception: Exception) {
                    // Nothing left to free.
                }
            }
            try {
                Thread.sleep(GlideUtils.RETRY_DELAY_MS * attempt)
            } catch (exception: InterruptedException) {
                return
            }
        }
    }

    private fun parseInt(value: String?): Int = value?.trim()?.toIntOrNull() ?: 0

    private fun showInfo(
        info: Info,
        box: AspectRatioFrameLayout,
        preview: ImageView,
        duration: TextView,
    ) {
        info.size?.let { box.ratio = it[1].toFloat() / it[0] }
        if (info.seconds > 0) {
            duration.text = YoutubeTitles.formatTime(info.seconds.toInt())
            if (box.tag == null) duration.visibility = View.VISIBLE
        }
        info.frame?.let { preview.setImageBitmap(it) }
    }

    /** What is known about a video, kept for when its comment is shown again. */
    private class Info(val size: IntArray?, val seconds: Long) {
        @Volatile var frame: Bitmap? = null
    }

    /** Plays the video in [box], under its buttons. Returns the player's view, or null. */
    @SuppressLint("ClickableViewAccessibility")
    private fun startPlayer(box: AspectRatioFrameLayout, id: String): PlayerView? {
        try {
            val context: Context = box.context
            currentPlayer?.let { pause(it) }

            val dataSourceFactory = DefaultDataSourceFactory(context, PLAYER_USER_AGENT)
            // A video file plays as it is; a comment video from its DASH playlist.
            val mediaSourceFactory: MediaSourceFactory =
                if (isFile(id)) ProgressiveMediaSource.Factory(dataSourceFactory)
                else DashMediaSource.Factory(dataSourceFactory)
            val mediaItem =
                MediaItem.fromUri(if (isFile(id)) id else videoUrl(id) + "/DASHPlaylist.mpd")
            val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)

            val player = SimpleExoPlayer.Builder(context).build()
            player.setMediaSource(mediaSource)
            player.prepare()
            player.setPlayWhenReady(true)

            val playerView = newPlayerView(context)
            playerView.setPlayer(player)
            box.addView(
                playerView,
                1,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )

            box.tag = player
            currentPlayer = player
            return playerView
        } catch (exception: Exception) {
            ErrorDialog.report(TAG, "Could not play the video", exception)
            return null
        }
    }

    private fun newPlayerView(context: Context): PlayerView {
        val playerView = PlayerView(context)
        playerView.setShowBuffering(SHOW_BUFFERING_WHEN_PLAYING)
        playerView.setShutterBackgroundColor(Color.TRANSPARENT)
        return playerView
    }

    private fun pause(player: SimpleExoPlayer) {
        try {
            player.setPlayWhenReady(false)
        } catch (exception: Exception) {
            Log.i(TAG, "Could not pause the video", exception)
        }
    }

    private fun releasePlayer(player: SimpleExoPlayer) {
        try {
            player.release()
        } catch (exception: Exception) {
            Log.i(TAG, "Could not release the video", exception)
        }
        if (currentPlayer === player) currentPlayer = null
    }

    /**
     * A button's icon over a video: a white sign on a dark circle, like the play button. Shrink is
     * "<", enlarge ">", full screen four outward corners and exit full screen four inward ones.
     */
    private class ButtonDrawable(kind: Int) : Drawable() {
        var kind: Int = kind
            set(value) {
                if (field == value) return
                field = value
                invalidateSelf()
            }

        private val circle = Paint(Paint.ANTI_ALIAS_FLAG)
        private val sign = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()

        init {
            circle.color = 0x99000000.toInt()
            sign.color = 0xFFFFFFFF.toInt()
            sign.style = Paint.Style.STROKE
            sign.strokeCap = Paint.Cap.ROUND
            sign.strokeJoin = Paint.Join.ROUND
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val r = Math.min(b.width(), b.height()) / 2f
            canvas.drawCircle(cx, cy, r, circle)
            sign.strokeWidth = r * 0.14f
            val t = r * 0.4f
            path.reset()
            when (kind) {
                SHRINK -> chevron(cx + t * 0.4f, cy, -t)
                ENLARGE -> chevron(cx - t * 0.4f, cy, t)
                else -> {
                    val c = t * 0.5f
                    val outward = kind == FULL_SCREEN
                    for (dx in intArrayOf(-1, 1)) {
                        for (dy in intArrayOf(-1, 1)) {
                            val x = cx + dx * t
                            val y = cy + dy * t
                            // Outward corners point away from the center; inward ones toward it.
                            val ix = if (outward) -dx * c else dx * c
                            val iy = if (outward) -dy * c else dy * c
                            val px = if (outward) x else x - dx * c
                            val py = if (outward) y else y - dy * c
                            path.moveTo(px + ix, py)
                            path.lineTo(px, py)
                            path.lineTo(px, py + iy)
                        }
                    }
                }
            }
            canvas.drawPath(path, sign)
        }

        /** A chevron with its tip at ([x] + [depth], [y]). */
        private fun chevron(x: Float, y: Float, depth: Float) {
            path.moveTo(x, y - Math.abs(depth))
            path.lineTo(x + depth, y)
            path.lineTo(x, y + Math.abs(depth))
        }

        override fun setAlpha(alpha: Int) {
            circle.alpha = alpha
            sign.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            sign.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        companion object {
            const val SHRINK = 0
            const val ENLARGE = 1
            const val FULL_SCREEN = 2
            const val EXIT_FULL_SCREEN = 3
        }
    }

    /**
     * What a Reddit video's DASH playlist says: its smallest video file, its duration and its size.
     */
    private class Playlist(val smallest: String, val seconds: Long, val size: IntArray?) {
        companion object {
            fun parse(mpd: String): Playlist {
                val parser = Xml.newPullParser()
                parser.setInput(StringReader(mpd))
                var seconds = -1L
                var smallest: String? = null
                var smallestBandwidth = Long.MAX_VALUE
                var bandwidth = Long.MAX_VALUE
                var size: IntArray? = null
                var video = false
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "MPD" ->
                                seconds =
                                    durationSeconds(
                                        parser.getAttributeValue(null, "mediaPresentationDuration")
                                    )
                            "Representation" -> {
                                // Audio renditions have no height.
                                video = parser.getAttributeValue(null, "height") != null
                                // Every rendition has the same ratio: the first video one's size.
                                val width = parser.getAttributeValue(null, "width")?.toIntOrNull()
                                val height = parser.getAttributeValue(null, "height")?.toIntOrNull()
                                if (
                                    size == null &&
                                        width != null &&
                                        height != null &&
                                        width > 0 &&
                                        height > 0
                                ) {
                                    size = intArrayOf(width, height)
                                }
                                bandwidth =
                                    parser.getAttributeValue(null, "bandwidth")?.toLong()
                                        ?: Long.MAX_VALUE
                            }
                            "BaseURL" ->
                                if (video && (smallest == null || bandwidth < smallestBandwidth)) {
                                    smallest = parser.nextText().trim()
                                    smallestBandwidth = bandwidth
                                }
                        }
                    }
                    event = parser.next()
                }
                return Playlist(
                    smallest ?: throw IllegalStateException("No video in the playlist"),
                    seconds,
                    size,
                )
            }

            /** The seconds of an ISO 8601 duration such as "PT1M2.5S", or -1. */
            fun durationSeconds(value: String?): Long {
                if (value == null || !value.startsWith("PT")) return -1
                var seconds = 0.0
                var start = 2
                try {
                    for (i in 2 until value.length) {
                        val unit = value[i]
                        if (unit != 'H' && unit != 'M' && unit != 'S') continue
                        val number = value.substring(start, i).toDouble()
                        seconds +=
                            when (unit) {
                                'H' -> number * 3600
                                'M' -> number * 60
                                else -> number
                            }
                        start = i + 1
                    }
                } catch (exception: NumberFormatException) {
                    return -1
                }
                return Math.round(seconds)
            }
        }
    }

    private fun fetchTextWithRetry(url: String): String {
        var attempt = 1
        while (true) {
            try {
                return fetchText(url)
            } catch (exception: Exception) {
                if (attempt >= GlideUtils.MAX_ATTEMPTS) throw exception
                Thread.sleep(GlideUtils.RETRY_DELAY_MS * attempt)
                attempt++
            }
        }
    }

    private fun fetchText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) throw IOException("HTTP $status")
            return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }
}
