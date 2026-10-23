// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("TextPostIcon")

package com.joancomasfdz.baconreader.textposticon

import android.widget.ImageView
import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.common.ErrorDialog
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "TextPostIcon"

/**
 * Called in the LinkHolder and LinkCardHolder constructors, right after the expand button of text
 * posts is found: replaces its icon with [PageDrawable].
 */
@Export
fun install(button: ImageView?) {
    if (button == null) return
    try {
        val original = button.drawable
        val density = button.resources.displayMetrics.density
        val size =
            if (original != null && original.intrinsicWidth > 0) {
                max(original.intrinsicWidth, original.intrinsicHeight)
            } else {
                (24 * density).roundToInt()
            }
        button.setImageDrawable(PageDrawable(iconColor(button), size, density))
        button.clearAnimation()
        button.rotation = 0f
        button.contentDescription = BaconReaderStrings.textPostIconContentDescription
    } catch (t: Throwable) {
        ErrorDialog.report(TAG, "Could not replace the text post icon", t)
    }
}

/**
 * Called at the start of rotateSelftextArrow(expanded, duration), which rotates the app's icon.
 * Flips the chevron instead and returns true, so the app's rotation is skipped, or returns false
 * when the button doesn't show [PageDrawable].
 */
@Export
fun setExpanded(button: ImageView?, expanded: Boolean, duration: Int): Boolean {
    val drawable = button?.drawable as? PageDrawable ?: return false
    button.clearAnimation()
    button.rotation = 0f
    drawable.setExpanded(expanded, duration > 0)
    return true
}

/** The color of the app's secondary icons and text in the current theme. */
private fun iconColor(view: ImageView): Int {
    val array = view.context.obtainStyledAttributes(intArrayOf(android.R.attr.textColorSecondary))
    try {
        return array.getColor(0, 0xFF888888.toInt())
    } finally {
        array.recycle()
    }
}
