// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("OpenLinks")

package com.joancomasfdz.baconreader.openlinks

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.ViewTreeObserver
import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.common.BaconReaderPatches
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.WeakHashMap

// Android 12 and later don't let an app turn on its own links, since reddit.com only verifies
// Reddit's official app. So BaconReader asks once whether to open the system "Open by default"
// screen, while no Reddit link opens in it.

private const val TAG = "OpenLinks"

private const val PREFERENCES = "baconreader_patches"
private const val PROMPT_SHOWN = "open_links_prompt_shown"

// After the app's own startup work (dialogs, consent, recreation) has settled.
private const val PROMPT_DELAY_MS = 1500L

// After another dialog closes, so the prompt doesn't pop up in the same instant.
private const val FOCUS_DELAY_MS = 600L

/** The prompt on screen, so a second resume doesn't open another one. */
private var showing = WeakReference<Dialog>(null)

/** Activities already waiting for their window to get the focus back. */
private val waiting = WeakHashMap<Activity, Boolean>()

/** Called at the start of MainActivity.onResume. */
@Export
fun onMainResume(activity: Activity?) {
    if (activity == null) return
    try {
        if (promptShown(activity)) return
        activity.window.decorView.postDelayed({ maybePrompt(activity) }, PROMPT_DELAY_MS)
    } catch (exception: Exception) {
        Log.i(TAG, "Could not schedule the links prompt", exception)
    }
}

private fun maybePrompt(activity: Activity) {
    try {
        if (activity.isFinishing || activity.isDestroyed) return
        if (showing.get()?.isShowing == true) return
        if (promptShown(activity)) return
        if (!activity.hasWindowFocus()) {
            // A permission request or the app's own dialog is on top: try again once it closes.
            waitForFocus(activity)
            return
        }
        val (enabled, total) = status(activity) ?: return
        if (total == 0 || enabled > 0) return

        val dialog =
            BaconReaderPatches.bundle()
                .ui(activity)
                .dialog()
                .title(BaconReaderStrings.openLinksPromptTitle)
                .message(BaconReaderStrings.openLinksPromptMessage)
                .action(BaconReaderStrings.openLinksPromptNotNow) { answered(activity) }
                .action(BaconReaderStrings.openLinksPromptSetUp) {
                    answered(activity)
                    openSettings(activity)
                }
                .show()
        // Only an answer stops the prompt: a dialog lost with its activity asks again.
        dialog.setOnCancelListener { answered(activity) }
        showing = WeakReference(dialog)
    } catch (exception: Exception) {
        Log.i(TAG, "Could not show the links prompt", exception)
    }
}

private fun waitForFocus(activity: Activity) {
    if (waiting.containsKey(activity)) return
    waiting[activity] = true
    val decor = activity.window.decorView
    decor.viewTreeObserver.addOnWindowFocusChangeListener(
        object : ViewTreeObserver.OnWindowFocusChangeListener {
            override fun onWindowFocusChanged(hasFocus: Boolean) {
                if (!hasFocus) return
                waiting.remove(activity)
                // A listener can't remove itself while the observer is calling it.
                decor.post { decor.viewTreeObserver.removeOnWindowFocusChangeListener(this) }
                decor.postDelayed({ maybePrompt(activity) }, FOCUS_DELAY_MS)
            }
        }
    )
}

/**
 * The Reddit links that open in the app and all the Reddit links it declares, or null when Android
 * doesn't tell, before Android 12.
 */
private fun status(context: Context): Pair<Int, Int>? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return try {
        val manager = context.getSystemService(DomainVerificationManager::class.java)
        val state = manager.getDomainVerificationUserState(context.packageName) ?: return null
        var enabled = 0
        var total = 0
        for ((host, hostState) in state.hostToStateMap) {
            // The app also declares links of other sites, such as google.com.
            if (!isRedditHost(host)) continue
            total++
            if (
                state.isLinkHandlingAllowed &&
                    hostState != DomainVerificationUserState.DOMAIN_STATE_NONE
            ) {
                enabled++
            }
        }
        enabled to total
    } catch (exception: Exception) {
        Log.i(TAG, "Could not read the state of the links", exception)
        null
    }
}

private fun isRedditHost(host: String?): Boolean {
    if (host == null) return false
    val name = host.lowercase(Locale.ROOT).removePrefix("*.")
    return name == "redd.it" ||
        name.endsWith(".redd.it") ||
        name == "reddit.com" ||
        name.endsWith(".reddit.com") ||
        name == "reddit.app.link" ||
        name == "redditmail.com" ||
        name.endsWith(".redditmail.com")
}

/** Opens the system "Open by default" screen of the app, or App info before Android 12. */
private fun openSettings(context: Context) {
    val uri = Uri.parse("package:" + context.packageName)
    val action =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS
        } else {
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        }
    try {
        context.startActivity(Intent(action, uri))
    } catch (exception: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
        } catch (ignored: Exception) {
            Log.i(TAG, "Could not open the app's settings", ignored)
        }
    }
}

private fun promptShown(context: Context): Boolean =
    preferences(context).getBoolean(PROMPT_SHOWN, false)

private fun answered(context: Context) {
    preferences(context).edit().putBoolean(PROMPT_SHOWN, true).apply()
}

private fun preferences(context: Context) =
    context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
