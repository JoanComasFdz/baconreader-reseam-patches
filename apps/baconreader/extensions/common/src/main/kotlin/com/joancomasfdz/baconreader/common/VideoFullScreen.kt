// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.common

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ui.PlayerControlView
import com.google.android.exoplayer2.ui.PlayerView
import com.joancomasfdz.baconreader.settings.BaconReaderStrings

/**
 * A video shown over the whole screen, from the player of a comment's video or of a post's: the
 * round buttons over a playing video, and the full screen they open. One shows at a time.
 */
object VideoFullScreen {
    private const val TAG = "VideoFullScreen"
    private const val BUTTON_SIZE_DP = 36
    private const val ORIENTATION_RELEASE_DELAY_MS = 1000L

    // PlayerView.setShowBuffering: show the spinner while buffering when playing.
    private const val SHOW_BUFFERING_WHEN_PLAYING = 1

    // Player.STATE_ENDED: the video played to its end.
    private const val STATE_ENDED = 4

    private val main = Handler(Looper.getMainLooper())

    // The full screen showing a video, if any, and the player and view it came from.
    private var fullScreen: Dialog? = null
    private var shownPlayer: SimpleExoPlayer? = null
    private var shownInline: PlayerView? = null

    /** Whether the video still plays in the view it came from, when the full screen closes. */
    fun interface Owner {
        fun owns(player: SimpleExoPlayer): Boolean
    }

    /** The full screen button over a playing video. [wide] tells, on tap, whether to turn it. */
    fun fullScreenButton(
        inline: PlayerView,
        player: SimpleExoPlayer,
        wide: () -> Boolean,
        owner: Owner,
    ): ImageView {
        val full = button(inline.context)
        full.setImageDrawable(ButtonDrawable(ButtonDrawable.FULL_SCREEN))
        full.contentDescription = BaconReaderStrings.embedVideoFullScreen
        full.setOnClickListener { open(inline, player, wide(), owner) }
        return full
    }

    /**
     * Shows the video of [player] over the whole screen, turned to landscape when it's [wide]. Back
     * or the button closes it, and the video plays on in [inline] while [owner] still has it.
     */
    private fun open(inline: PlayerView, player: SimpleExoPlayer, wide: Boolean, owner: Owner) {
        try {
            val activity = activityOf(inline.context) ?: return
            close()

            val root = FrameLayout(activity)
            root.setBackgroundColor(Color.BLACK)
            val playerView = PlayerView(activity)
            playerView.setShowBuffering(SHOW_BUFFERING_WHEN_PLAYING)
            playerView.setShutterBackgroundColor(Color.TRANSPARENT)
            playerView.id = resourceId(activity, "baconreader_patches_video_full_screen")
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
            val before =
                if (
                    activity.resources.configuration.orientation ==
                        Configuration.ORIENTATION_LANDSCAPE
                ) {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            if (wide) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            dialog.setOnDismissListener {
                if (fullScreen === dialog) {
                    fullScreen = null
                    shownPlayer = null
                    shownInline = null
                }
                if (wide) {
                    // Given back right away, the app's own orientation follows the sensor, which
                    // keeps landscape while the phone lies flat. The screen turns back first.
                    activity.requestedOrientation = before
                    main.postDelayed(
                        { if (fullScreen == null) activity.requestedOrientation = orientation },
                        ORIENTATION_RELEASE_DELAY_MS,
                    )
                }
                if (owner.owns(player)) PlayerView.switchTargetView(player, playerView, inline)
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
            shownPlayer = player
            shownInline = inline
        } catch (exception: Exception) {
            ErrorDialog.report(TAG, "Could not show the video in full screen", exception)
        }
    }

    /** Closes the full screen when it shows [player], or the video of [inline]. */
    fun closeShowing(player: SimpleExoPlayer?, inline: PlayerView?) {
        if (fullScreen == null) return
        if (
            (player != null && player === shownPlayer) || (inline != null && inline === shownInline)
        ) {
            close()
        }
    }

    private fun close() {
        val dialog = fullScreen ?: return
        fullScreen = null
        shownPlayer = null
        shownInline = null
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

    /**
     * The id a patch adds to the app's resources as [name], for the e2e tests to find a view by.
     */
    private fun resourceId(context: Context, name: String): Int {
        val id = context.resources.getIdentifier(name, "id", context.packageName)
        return if (id == 0) View.NO_ID else id
    }

    /** Shows [buttons] while the player's controls show, and hides them with the controls. */
    fun followControls(playerView: PlayerView, buttons: View) {
        buttons.visibility = if (playerView.isControllerVisible) View.VISIBLE else View.GONE
        playerView.setControllerVisibilityListener(
            PlayerControlView.VisibilityListener { visibility -> buttons.visibility = visibility }
        )
    }

    fun button(context: Context): ImageView {
        val button = ImageView(context)
        button.scaleType = ImageView.ScaleType.FIT_CENTER
        return button
    }

    fun buttonRow(context: Context, vararg buttons: ImageView): LinearLayout {
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

    /** The buttons' place over a video: its top right corner. */
    fun buttonRowParams(context: Context): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            )
            .apply {
                val margin = dp(context, 6)
                setMargins(margin, margin, margin, margin)
            }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    /**
     * A button's icon over a video: a white sign on a dark circle, like the play button. Shrink is
     * "<", enlarge ">", full screen four outward corners and exit full screen four inward ones.
     */
    class ButtonDrawable(kind: Int) : Drawable() {
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
}
