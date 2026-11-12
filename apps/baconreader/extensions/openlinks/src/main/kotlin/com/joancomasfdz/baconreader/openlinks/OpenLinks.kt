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
import android.os.Parcelable
import android.provider.Settings
import android.util.Log
import android.view.ViewTreeObserver
import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.common.BaconReaderPatches
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.joancomasfdz.baconreader.settings.LinkSettings
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

/**
 * Called at the start of LinkProcessor.getIntent. With "Open web pages in the browser" on, returns
 * the intent that opens a web page in the user's browser, or, while "Open links inside app" is off,
 * the one that opens an image, GIF or video in BaconReader's own viewer, as the app does with that
 * setting on. Returns null to let the app decide: always for a link to Reddit.
 */
@Export
fun webPageIntent(context: Context?, url: String?, forceBrowser: Boolean): Intent? {
    if (context == null || url == null || forceBrowser) return null
    if (!LinkSettings.openWebPagesInBrowser.value) return null
    try {
        val uri = Uri.parse(url)
        return when (kindOf(uri)) {
            Kind.WEB -> Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            Kind.MEDIA -> if (internalBrowser()) null else viewer(context).setData(uri)
            else -> null
        }
    } catch (exception: Exception) {
        Log.w(TAG, "Could not read the link $url", exception)
        return null
    }
}

/**
 * Called at the start of LinkProcessor.getBrowserIntent, the link a post's header and its link
 * button open. The same rule as [webPageIntent], for a post's link.
 */
@Export
fun webLinkIntent(context: Context?, link: Any?): Intent? {
    val url =
        try {
            // Link.url is a public field, with no getter.
            link?.javaClass?.getField("url")?.get(link) as String?
        } catch (exception: Exception) {
            Log.w(TAG, "Could not read the link", exception)
            null
        }
    // A Reddit video or gallery, which the app shows itself whatever the post's address.
    if (link != null && showsOwnMedia(link)) return null
    val intent = webPageIntent(context, url, false) ?: return null
    // The app's viewer also takes the post, as getBrowserIntent gives it.
    if (intent.component != null && link is Parcelable) intent.putExtra("link", link)
    return intent
}

/**
 * Called where the text of posts and comments reads "Open links inside app" for a link
 * (RedditSpanner.internalBrowser), which decides whether it opens through LinkProcessor. With "Open
 * web pages in the browser" on, an image, GIF or video does, whatever that setting says, so
 * [webPageIntent] opens it in the app.
 */
@Export
fun mediaOpensInApp(inApp: Boolean, element: Any?): Boolean {
    if (inApp || element == null || !LinkSettings.openWebPagesInBrowser.value) return inApp
    try {
        var href =
            element.javaClass.getMethod("getAttribute", String::class.java).invoke(element, "href")
                as String? ?: return false
        href = href.replace("&amp;", "&")
        if (href.startsWith("//")) href = "https:$href"
        return kindOf(Uri.parse(href)) == Kind.MEDIA
    } catch (exception: Exception) {
        Log.w(TAG, "Could not read the link", exception)
        return inApp
    }
}

/**
 * Whether the post is a Reddit video or gallery: Link.isRedditVideo,
 * LinkProcessor.isValidRedditGallery.
 */
private fun showsOwnMedia(link: Any): Boolean =
    try {
        val video = link.javaClass.getMethod("isRedditVideo").invoke(link) as Boolean
        val gallery =
            Class.forName("com.onelouder.baconreader.LinkProcessor")
                .getMethod("isValidRedditGallery", link.javaClass)
                .invoke(null, link) as Boolean
        video || gallery
    } catch (exception: Exception) {
        Log.w(TAG, "Could not check the media of the post", exception)
        false
    }

private enum class Kind {
    WEB,
    MEDIA,
    REDDIT,
    OTHER,
}

// Reddit's pages, which the app opens itself.
private val REDDIT_HOSTS = listOf("reddit.com", "redd.it")

// The hosts of images, GIFs, galleries and videos BaconReader shows in its own viewer, and the
// files
// it shows or plays.
private val MEDIA_HOSTS = listOf("i.redd.it", "preview.redd.it", "imgur.com", "redgifs.com")
private val MEDIA_FILES = setOf("jpg", "jpeg", "png", "gif", "gifv", "webp", "mp4", "webm")

private fun kindOf(uri: Uri): Kind {
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    if (scheme != "http" && scheme != "https") return Kind.OTHER
    val host = uri.host?.lowercase(Locale.ROOT) ?: return Kind.OTHER
    val file = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
    if (MEDIA_HOSTS.any { matches(host, it) } || file in MEDIA_FILES) return Kind.MEDIA
    if (REDDIT_HOSTS.any { matches(host, it) }) return Kind.REDDIT
    return Kind.WEB
}

private fun matches(host: String, domain: String) = host == domain || host.endsWith(".$domain")

private const val BROWSER_ACTIVITY = "com.onelouder.baconreader.BrowserActivity"

/** BaconReader's own viewer, which "Open links inside app" opens a link in. */
private fun viewer(context: Context): Intent = Intent().setClassName(context, BROWSER_ACTIVITY)

/** BaconReader's "Open links inside app" (Preferences.getInternalBrowser). */
private fun internalBrowser(): Boolean =
    try {
        Class.forName("com.onelouder.baconreader.Preferences")
            .getMethod("getInternalBrowser")
            .invoke(null) as Boolean
    } catch (exception: Exception) {
        Log.w(TAG, "Could not read Open links inside app", exception)
        true
    }

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
