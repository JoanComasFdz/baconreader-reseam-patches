// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("RedditLinks")

package com.joancomasfdz.baconreader.redditlinks

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import app.reseam.runtime.Export
import com.onelouder.baconreader.BrowserActivity
import com.onelouder.baconreader.DetailActivity
import com.onelouder.baconreader.LinkDispatcher
import com.onelouder.baconreader.LinkProcessor
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "RedditLinks"

// Share links resolve on www.reddit.com whatever host the link names.
private const val REDDIT = "https://www.reddit.com"

private const val EXTRA_SHARE_LINK = "com.joancomasfdz.baconreader.redditlinks.SHARE_LINK"

private const val TIMEOUT_MS = 10_000

/**
 * Called at the start of LinkProcessor.getIntent, with its fields. Returns the intent for the
 * Reddit links BaconReader doesn't recognize, or null to let the app decide.
 */
@Export
fun intent(context: Context?, url: String?, forceBrowser: Boolean): Intent? {
    if (context == null || url == null || forceBrowser) return null
    try {
        if (!LinkProcessor.isReddit(url)) return null
        val uri = Uri.parse(url)
        val segments = uri.pathSegments ?: return null
        val first = segments.getOrNull(0)
        val isUser = first == "u" || first == "user"
        return when {
            // /r/<sub>/s/<code> and /u/<user>/s/<code>: Reddit's share links, which only say what
            // they open through a redirect.
            (first == "r" || isUser) && segments.size == 4 && segments[2] == "s" ->
                Intent(context, LinkDispatcher::class.java)
                    .setData(uri)
                    .putExtra(EXTRA_SHARE_LINK, true)
            // /comments/<post>[/<title>[/<comment>]]: a post without its subreddit.
            first == "comments" && segments.size >= 2 ->
                detail(context, uri, segments[1], segments.getOrNull(3))
            // /gallery/<post>
            first == "gallery" && segments.size == 2 -> detail(context, uri, segments[1], null)
            // /user/<user>/comments/<post>[/<title>[/<comment>]]: a post on a user's profile,
            // which the app opens as the profile.
            isUser && segments.size >= 4 && segments[2] == "comments" ->
                detail(context, uri, segments[3], segments.getOrNull(5))
            else -> null
        }
    } catch (exception: Exception) {
        Log.w(TAG, "Could not read the link $url", exception)
        return null
    }
}

private fun detail(context: Context, uri: Uri, post: String, comment: String?): Intent? {
    if (post.isEmpty()) return null
    val intent =
        Intent(context, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_LINKID, post)
    intent.putExtra(
        DetailActivity.EXTRA_PARENTS,
        uri.getQueryParameter("context")?.toIntOrNull() ?: 0,
    )
    if (!comment.isNullOrEmpty()) intent.putExtra(DetailActivity.EXTRA_COMMENTID, comment)
    return intent
}

/**
 * Called at the start of LinkDispatcher.processing. Opens a share link where it leads, once its
 * redirect is known, and returns true; returns false for every other intent.
 */
@Export
fun dispatch(activity: Activity?, intent: Intent?): Boolean {
    if (activity == null || intent == null || !intent.getBooleanExtra(EXTRA_SHARE_LINK, false)) {
        return false
    }
    val link = intent.data ?: return false
    Thread {
        val target = resolve(link)
        activity.runOnUiThread { open(activity, link, target) }
    }
        .start()
    return true
}

private fun open(activity: Activity, link: Uri, target: String?) {
    try {
        val next =
            target?.let { LinkProcessor(activity, it).intent }
                ?: Intent(activity, BrowserActivity::class.java).setData(link)
        activity.startActivity(next)
    } catch (exception: Exception) {
        Log.w(TAG, "Could not open the share link $link", exception)
    }
    activity.finish()
}

/** The link the share link redirects to, on the share link's host, or null. */
private fun resolve(link: Uri): String? {
    var connection: HttpURLConnection? = null
    try {
        val request = URL(REDDIT + link.encodedPath + (link.encodedQuery?.let { "?$it" } ?: ""))
        connection = request.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        val status = connection.responseCode
        val location = connection.getHeaderField("Location")
        if (status !in 300..399 || location.isNullOrEmpty()) {
            Log.w(TAG, "The share link $link answered $status without a redirect")
            return null
        }
        // Rebuilt on the link's own host, so the app recognizes it as a Reddit link.
        val target = Uri.parse(URL(request, location).toString())
        return Uri.Builder()
            .scheme(link.scheme)
            .encodedAuthority(link.encodedAuthority)
            .encodedPath(target.encodedPath)
            .encodedQuery(target.encodedQuery)
            .build()
            .toString()
    } catch (exception: Exception) {
        Log.w(TAG, "Could not resolve the share link $link", exception)
        return null
    } finally {
        connection?.disconnect()
    }
}
