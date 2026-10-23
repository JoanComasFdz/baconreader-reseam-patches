// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("SettingsEntry")

package com.joancomasfdz.baconreader.settingsentry

import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.common.BaconReaderPatches
import com.joancomasfdz.baconreader.common.ErrorDialog
import com.joancomasfdz.baconreader.settings.BaconReaderStrings

/** BaconReader's settings sections, then the patches' entry. */
@Export
fun withEntry(sections: Array<String?>?): Array<String?> =
    (sections ?: emptyArray()) + BaconReaderStrings.settingsEntry

/** Whether the menu position is the patches' entry, which comes after BaconReader's sections. */
@Export fun isEntry(sections: Array<String?>?, position: Int): Boolean = position == sections?.size

// Marks the footer, which each drawer gets once.
private const val FOOTER_TAG = "baconreader_patches_version"

/**
 * Called by DrawerHandler.updateDrawer with the drawer's list: shows the patches' version under it,
 * at the bottom of the drawer, to tell which build is installed. [textColor] is the theme attribute
 * of the drawer's text color.
 */
@Export
fun addVersionFooter(list: ListView?, textColor: Int) {
    val drawer = list?.parent as? LinearLayout ?: return
    if (drawer.findViewWithTag<View>(FOOTER_TAG) != null) return
    try {
        val context = list.context
        val value = TypedValue()
        val color =
            if (context.theme.resolveAttribute(textColor, value, true)) value.data else Color.GRAY
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val footer =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                tag = FOOTER_TAG
            }
        footer.addView(
            View(context).apply { setBackgroundColor(withAlpha(color, 0x1F)) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, maxOf(1, dp(1) / 2)),
        )
        footer.addView(
            TextView(context).apply {
                text = BaconReaderStrings.drawerPatchesVersion(BaconReaderPatches.version(context))
                setTextColor(withAlpha(color, 0x99))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(16), dp(12), dp(16), dp(12))
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        // The list takes the height the footer leaves.
        list.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        drawer.addView(footer)
    } catch (exception: Exception) {
        ErrorDialog.report("SettingsEntry", "Could not show the patches' version", exception)
    }
}

private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)
