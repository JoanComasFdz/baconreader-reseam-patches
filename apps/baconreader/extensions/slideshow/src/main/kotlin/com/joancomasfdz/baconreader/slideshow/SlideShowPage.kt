// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.slideshow

import android.content.res.Configuration
import android.graphics.Point
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import java.util.WeakHashMap

/**
 * A post's page in the Slide Show View. BaconReader sizes it from Display.getCurrentSizeRange, less
 * its toolbar and bottom bar. That size used to leave out the status and navigation bars; on newer
 * Android versions (seen on Android 16 and 17) it is the whole screen, so the page runs on behind
 * the bottom bar and the navigation bar. The page is shortened by what the size range reports
 * beyond the window's height without its system bars.
 */
internal object SlideShowPage {
    private const val TAG = "FixSlideShow"

    // Each page's last fit: the height BaconReader gave it, its orientation and what was taken off.
    // Getting the window's insets asks the system, and the list asks for the page's layout params
    // on every layout pass.
    private val fits = WeakHashMap<View, IntArray>()

    fun fit(page: View, params: ViewGroup.LayoutParams) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || params.height <= 0) return
        val orientation = page.resources.configuration.orientation
        val last = fits[page]
        val excess =
            if (last != null && last[0] == params.height && last[1] == orientation) last[2]
            else
                excess(page, orientation).also {
                    fits[page] = intArrayOf(params.height, orientation, it)
                }
        if (excess > 0) params.height -= excess
    }

    private fun excess(page: View, orientation: Int): Int =
        try {
            val windowManager = page.context.getSystemService(WindowManager::class.java)
            val metrics = windowManager.currentWindowMetrics
            val bars =
                metrics.windowInsets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
            val available = metrics.bounds.height() - bars.top - bars.bottom
            val smallest = Point()
            val largest = Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getCurrentSizeRange(smallest, largest)
            val reported =
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) smallest.y else largest.y
            Math.max(0, reported - available)
        } catch (exception: Exception) {
            Log.i(TAG, "Could not measure the window", exception)
            0
        }
}
