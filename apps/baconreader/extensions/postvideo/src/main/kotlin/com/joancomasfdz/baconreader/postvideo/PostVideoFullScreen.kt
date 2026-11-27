// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("PostVideoFullScreen")

package com.joancomasfdz.baconreader.postvideo

import android.view.View
import android.widget.FrameLayout
import app.reseam.runtime.Export
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ui.PlayerView
import com.joancomasfdz.baconreader.common.ErrorDialog
import com.joancomasfdz.baconreader.common.VideoFullScreen
import com.joancomasfdz.baconreader.settings.PostVideoFullScreenSettings
import java.util.WeakHashMap

/*
 * The video of a post's detail view gets the full screen button of a comment's video, over its top
 * right corner while its controls show. PostMediaHandler is also used by the full-screen photo
 * viewer, already full screen, and the browser, which keep their player as it is.
 */

private const val TAG = "PostVideoFullScreen"

// The listener DetailLinkFragment gives its PostMediaHandler. PhotoSlide and BrowserActivity give
// their own.
private const val DETAIL_LISTENER = "com.onelouder.baconreader.adapters.DetailLinkFragment\$6"

/** The buttons over each post's player view, and the player it shows. */
private val rows = WeakHashMap<PlayerView, View>()
private val players = WeakHashMap<PlayerView, SimpleExoPlayer>()

/** Adds the full screen button over the post's video, once PostMediaHandler plays it. */
@Export
fun addButton(listener: Any?, videoView: PlayerView?, player: SimpleExoPlayer?) {
    if (videoView == null || player == null) return
    if (listener?.javaClass?.name != DETAIL_LISTENER) return
    if (!PostVideoFullScreenSettings.postVideoFullScreenButton.value) return
    try {
        // The page of the media's ViewFlipper that holds the player and its mute button.
        val page = videoView.parent as? FrameLayout ?: return
        rows.remove(videoView)?.let { page.removeView(it) }
        players[videoView] = player
        val context = videoView.context
        val full =
            VideoFullScreen.fullScreenButton(
                videoView,
                player,
                { isWide(videoView) },
                VideoFullScreen.Owner { players[videoView] === it },
            )
        val row = VideoFullScreen.buttonRow(context, full)
        page.addView(row, VideoFullScreen.buttonRowParams(context))
        rows[videoView] = row
        VideoFullScreen.followControls(videoView, row)
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not add the full screen button", exception)
    }
}

/** Closes the full screen of the post's video before PostMediaHandler releases its player. */
@Export
fun release(videoView: PlayerView?) {
    if (videoView == null) return
    VideoFullScreen.closeShowing(null, videoView)
    players.remove(videoView)
}

/** Whether the video is wider than high: the player fits its surface to the video's shape. */
private fun isWide(videoView: PlayerView): Boolean {
    val surface = videoView.videoSurfaceView ?: return false
    return surface.width > surface.height
}
