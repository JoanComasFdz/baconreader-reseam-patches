// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("DetailVideo")

package com.joancomasfdz.baconreader.detailvideo

import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.settings.DetailVideoSettings
import java.util.Collections
import java.util.WeakHashMap

/*
 * With "Autoplay videos in Detail View" off, a video in a post's detail view loads and shows its
 * first frame, but doesn't play until the user taps play or restart. PostMediaHandler is also used
 * by the full-screen photo viewer and the browser, which keep playing.
 */

// The listener DetailLinkFragment gives its PostMediaHandler. PhotoSlide and BrowserActivity give
// their own.
private const val DETAIL_LISTENER = "com.onelouder.baconreader.adapters.DetailLinkFragment\$6"

// The VideoHandlers whose video the user started after the detail view held it.
private val started: MutableSet<Any> = Collections.newSetFromMap(WeakHashMap())

private fun holds(listener: Any?) =
    listener?.javaClass?.name == DETAIL_LISTENER &&
        !DetailVideoSettings.autoplayVideosInDetailView.value

/**
 * Whether the player starts when the app creates it, in place of what the app asked for. The app
 * asks to play when the post is the one on screen.
 */
@Export
fun playWhenReady(listener: Any?, videoHandler: Any?, requested: Boolean): Boolean {
    if (!holds(listener)) return requested
    started.remove(videoHandler)
    return false
}

/**
 * Whether the app's resume of a video is skipped: when the post comes on screen, or BaconReader
 * comes back to the foreground.
 */
@Export
fun holdsVideo(listener: Any?, videoHandler: Any?): Boolean =
    holds(listener) && videoHandler != null && videoHandler !in started

/** The user pressed play, pause or restart: the app's own resume rules apply again. */
@Export
fun onUserControl(videoHandler: Any?) {
    if (videoHandler != null) started.add(videoHandler)
}
