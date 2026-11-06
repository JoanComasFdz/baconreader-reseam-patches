// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("AccountColors")

package com.joancomasfdz.baconreader.accountcolors

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Adapter
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.common.ErrorDialog
import com.joancomasfdz.baconreader.settings.AccountColorsSettings
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.onelouder.baconreader.adapters.DrawerAdapter
import com.onelouder.baconreader.data.SessionManager
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.roundToInt

private const val TAG = "AccountColors"

private const val PREFERENCES = "baconreader_patches"
private const val KEY_PREFIX = "account_colors.color."

// DrawerAdapter's account row type, and DrawerHandler's ACTION_ACCOUNT, the tag of the rows of the
// accounts. "Log out" and "Add account" are rows of the same type with other tags.
private const val TYPE_ACCOUNT = 2
private const val TAG_ACCOUNT = 110

private const val CIRCLE_DP = 20
private const val CIRCLE_TOUCH_DP = 40
private const val SWATCH_DP = 36
private const val SWATCH_CELL_DP = 56
private const val LINE_DP = 3

/** The colors to pick from, with the names that describe them to TalkBack and the tests. */
private val palette =
    listOf(
        0xFFF44336.toInt() to BaconReaderStrings.accountColorRed,
        0xFFE91E63.toInt() to BaconReaderStrings.accountColorPink,
        0xFF9C27B0.toInt() to BaconReaderStrings.accountColorPurple,
        0xFF3F51B5.toInt() to BaconReaderStrings.accountColorIndigo,
        0xFF2196F3.toInt() to BaconReaderStrings.accountColorBlue,
        0xFF00BCD4.toInt() to BaconReaderStrings.accountColorCyan,
        0xFF009688.toInt() to BaconReaderStrings.accountColorTeal,
        0xFF4CAF50.toInt() to BaconReaderStrings.accountColorGreen,
        0xFFCDDC39.toInt() to BaconReaderStrings.accountColorLime,
        0xFFFFEB3B.toInt() to BaconReaderStrings.accountColorYellow,
        0xFFFF9800.toInt() to BaconReaderStrings.accountColorOrange,
        0xFF795548.toInt() to BaconReaderStrings.accountColorBrown,
    )

private var preferences: SharedPreferences? = null
private var toolbarId = 0
private var iconId = 0
private var resumed = WeakReference<Activity>(null)

/** The circle added to each account row the drawer's list made, by row. */
private val circles = WeakHashMap<View, ImageView>()

/** The line drawn over each toolbar, by toolbar. */
private val lines = WeakHashMap<View, ToolbarLine>()

/**
 * Called once the app starts, with the ids of the toolbars and of the account rows' ✕: draws the
 * account's line under the toolbar of each screen as it comes to the front.
 */
@Export
fun init(application: Application?, toolbar: Int, accountIcon: Int) {
    if (application == null) return
    preferences = application.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    toolbarId = toolbar
    iconId = accountIcon
    application.registerActivityLifecycleCallbacks(
        object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                resumed = WeakReference(activity)
                showLine(activity)
            }

            override fun onActivityCreated(activity: Activity, state: Bundle?) {}

            override fun onActivityStarted(activity: Activity) {}

            override fun onActivityPaused(activity: Activity) {}

            override fun onActivityStopped(activity: Activity) {}

            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {}
        }
    )
}

/**
 * Called after DrawerAdapter.getView with the row it returns: adds the circle between an account's
 * name and its ✕, or hides it on the other rows of the same layout.
 */
@Export
fun bindRow(adapter: Adapter?, position: Int, row: View?) {
    try {
        val layout = row as? RelativeLayout ?: return
        val item = adapter?.getItem(position) as? DrawerAdapter.DrawerItem
        val name = item?.takeIf { it.type == TYPE_ACCOUNT && it.tag == TAG_ACCOUNT }?.tagString
        val existing = circles[layout]
        if (name.isNullOrEmpty() || !AccountColorsSettings.accountColors.value) {
            existing?.visibility = View.GONE
            return
        }
        val circle = existing ?: addCircle(layout) ?: return
        circle.visibility = View.VISIBLE
        showCircle(circle, name)
        circle.setOnClickListener { pickColor(circle, name) }
    } catch (t: Throwable) {
        ErrorDialog.report(TAG, "Could not show the account's color", t)
    }
}

/**
 * Called by DrawerHandler.updateDrawer, which runs again whenever the account changes: redraws the
 * line of the screen in front.
 */
@Export
fun onDrawerUpdated() {
    resumed.get()?.let(::showLine)
}

/** Called when an account is removed from the app: forgets its color. */
@Export
fun onAccountRemoved(name: String?) {
    if (name.isNullOrEmpty()) return
    preferences?.edit()?.remove(key(name))?.apply()
}

private fun addCircle(row: RelativeLayout): ImageView? {
    val icon = row.findViewById<View>(iconId) ?: return null
    val density = row.resources.displayMetrics.density
    val circle = ImageView(row.context)
    circle.id = View.generateViewId()
    circle.scaleType = ImageView.ScaleType.CENTER
    val params =
        RelativeLayout.LayoutParams(
            dp(CIRCLE_TOUCH_DP, density),
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    params.addRule(RelativeLayout.LEFT_OF, icon.id)
    params.addRule(RelativeLayout.CENTER_VERTICAL)
    params.rightMargin = dp(8, density)
    row.addView(circle, params)
    circles[row] = circle
    return circle
}

private fun showCircle(circle: ImageView, name: String) {
    circle.setImageDrawable(circleDrawable(circle, colorOf(name), CIRCLE_DP, selected = false))
    circle.contentDescription = BaconReaderStrings.accountColorDescription(name)
}

private fun pickColor(circle: ImageView, name: String) {
    try {
        val context = circle.context
        val density = context.resources.displayMetrics.density
        val current = colorOf(name)
        val grid = GridLayout(context)
        grid.columnCount = 4
        val padding = dp(16, density)
        grid.setPadding(padding, padding, padding, 0)
        var dialog: AlertDialog? = null
        for ((color, colorName) in palette) {
            val swatch = ImageView(context)
            swatch.scaleType = ImageView.ScaleType.CENTER
            swatch.setImageDrawable(circleDrawable(swatch, color, SWATCH_DP, color == current))
            swatch.contentDescription = colorName
            swatch.setOnClickListener {
                setColor(name, color)
                showCircle(circle, name)
                dialog?.dismiss()
            }
            grid.addView(
                swatch,
                ViewGroup.LayoutParams(dp(SWATCH_CELL_DP, density), dp(SWATCH_CELL_DP, density)),
            )
        }
        dialog =
            AlertDialog.Builder(context)
                .setTitle(BaconReaderStrings.accountColorTitle(name))
                .setView(centered(grid))
                .setNegativeButton(BaconReaderStrings.accountColorNone) { _, _ ->
                    setColor(name, null)
                    showCircle(circle, name)
                }
                .show()
    } catch (t: Throwable) {
        ErrorDialog.report(TAG, "Could not show the account colors", t)
    }
}

/** The swatches in the middle of the dialog, which is wider than them. */
private fun centered(grid: GridLayout): View {
    val frame = FrameLayout(grid.context)
    frame.addView(
        grid,
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL,
        ),
    )
    return frame
}

private fun setColor(name: String, color: Int?) {
    val editor = preferences?.edit() ?: return
    if (color == null) editor.remove(key(name)) else editor.putInt(key(name), color)
    editor.apply()
    onDrawerUpdated()
}

private fun showLine(activity: Activity) {
    try {
        val toolbar = activity.findViewById<View>(toolbarId) ?: return
        val color =
            if (AccountColorsSettings.accountColors.value) {
                SessionManager.getUsername()?.let(::colorOf)
            } else {
                null
            }
        val line = lines[toolbar]
        if (color == null) {
            line?.remove()
            lines.remove(toolbar)
        } else {
            (line ?: ToolbarLine(toolbar).also { lines[toolbar] = it }).show(color)
        }
    } catch (t: Throwable) {
        ErrorDialog.report(TAG, "Could not show the account's color on the toolbar", t)
    }
}

/** A line of the account's color along the bottom of a toolbar, over its own background. */
private class ToolbarLine(private val toolbar: View) {
    private val drawable = ColorDrawable()
    private val height = dp(LINE_DP, toolbar.resources.displayMetrics.density)
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        place()
    }

    init {
        toolbar.overlay.add(drawable)
        toolbar.addOnLayoutChangeListener(layoutListener)
        place()
    }

    fun show(color: Int) {
        drawable.color = color
        toolbar.invalidate()
    }

    fun remove() {
        toolbar.overlay.remove(drawable)
        toolbar.removeOnLayoutChangeListener(layoutListener)
    }

    private fun place() {
        drawable.setBounds(0, toolbar.height - height, toolbar.width, toolbar.height)
    }
}

/** A filled circle of [color], or an outlined one in the theme's secondary color without one. */
private fun circleDrawable(
    view: View,
    color: Int?,
    sizeDp: Int,
    selected: Boolean,
): GradientDrawable {
    val density = view.resources.displayMetrics.density
    val circle = GradientDrawable()
    circle.shape = GradientDrawable.OVAL
    val size = dp(sizeDp, density)
    circle.setSize(size, size)
    if (color == null) {
        circle.setColor(Color.TRANSPARENT)
        circle.setStroke(dp(2, density), secondaryColor(view))
    } else {
        circle.setColor(color)
        if (selected) circle.setStroke(dp(3, density), secondaryColor(view))
    }
    return circle
}

private fun colorOf(name: String): Int? {
    val preferences = preferences ?: return null
    val key = key(name)
    return if (preferences.contains(key)) preferences.getInt(key, 0) else null
}

// Reddit's user names are the same in any case.
private fun key(name: String) = KEY_PREFIX + name.lowercase(Locale.ROOT)

private fun dp(value: Int, density: Float) = (value * density).roundToInt()

/** The color of the app's secondary icons and text in the current theme. */
private fun secondaryColor(view: View): Int {
    val array = view.context.obtainStyledAttributes(intArrayOf(android.R.attr.textColorSecondary))
    try {
        return array.getColor(0, 0xFF888888.toInt())
    } finally {
        array.recycle()
    }
}
