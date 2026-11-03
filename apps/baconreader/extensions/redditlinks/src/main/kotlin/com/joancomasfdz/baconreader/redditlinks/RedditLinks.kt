// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("RedditLinks")

package com.joancomasfdz.baconreader.redditlinks

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import app.reseam.runtime.Export
import com.onelouder.baconreader.BrowserActivity
import com.onelouder.baconreader.DetailActivity
import com.onelouder.baconreader.LinkDispatcher
import com.onelouder.baconreader.LinkProcessor
import com.onelouder.baconreader.RequestMethod
import com.onelouder.baconreader.data.SessionManager
import com.onelouder.baconreader.data.SessionToken
import com.onelouder.baconreader.reddit.Link
import com.onelouder.baconreader.reddit.RedditApi
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

private const val TAG = "RedditLinks"

// Share links resolve on www.reddit.com whatever host the link names.
private const val REDDIT = "https://www.reddit.com"

private const val VIDEO_HOST = "v.redd.it"

private const val EXTRA_SHARE_LINK = "com.joancomasfdz.baconreader.redditlinks.SHARE_LINK"
private const val EXTRA_VIDEO_LINK = "com.joancomasfdz.baconreader.redditlinks.VIDEO_LINK"

private const val TIMEOUT_MS = 10_000

/**
 * The ID of a post's own gallery or Reddit video, the last segment of its address, which the app is
 * opening in its media viewer: [intent] leaves it to the app instead of opening the post again. The
 * next [intent] call takes it, so it never applies to a later link. LinkProcessor rewrites some
 * Reddit addresses, so the ID is compared, not the address.
 */
private val appMedia = ThreadLocal<String?>()

/**
 * Called at the start of LinkProcessor.getIntent, with its fields. Returns the intent for the
 * Reddit links BaconReader doesn't recognize, or null to let the app decide.
 */
@Export
fun intent(context: Context?, url: String?, forceBrowser: Boolean): Intent? {
    val keep = appMedia.get()
    appMedia.remove()
    if (context == null || url == null || forceBrowser) return null
    try {
        val uri = Uri.parse(url)
        if (keep != null && uri.lastPathSegment == keep) return null
        // v.redd.it/<video>: the app looks up its post through www.reddit.com/video/<video>, which
        // Reddit refuses (403).
        if (uri.host == VIDEO_HOST && uri.pathSegments?.size == 1) {
            return Intent(context, LinkDispatcher::class.java)
                .setData(uri)
                .putExtra(EXTRA_VIDEO_LINK, true)
        }
        if (!LinkProcessor.isReddit(url)) return null
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
 * Called at the start of LinkProcessor.getBrowserIntent(Context, Link) and of the post header's
 * DetailLinkHelper.onOpen(Link), which open a post's media from its thumbnail. A gallery or Reddit
 * video the post carries opens in the app's media viewer, as before [intent] turned its link into
 * the post itself.
 */
@Export
fun keepAppMedia(link: Link?) {
    appMedia.set(link?.takeIf(::showsOwnMedia)?.url?.let { Uri.parse(it).lastPathSegment })
}

private fun showsOwnMedia(link: Link): Boolean =
    try {
        LinkProcessor.isValidRedditGallery(link) || link.isRedditVideo()
    } catch (exception: Exception) {
        Log.w(TAG, "Could not check the media of the post", exception)
        false
    }

/**
 * Called at the start of ClickableLinkSpan.onClick, the link next to a post's title, which starts
 * only the intents that open the browser. Opens the links [intent] knows and returns true; returns
 * false to let the span go on, and the post's own gallery or video opens in the app's media viewer
 * ([keepAppMedia]).
 */
@Export
fun open(view: View?, link: Link?): Boolean {
    val context = view?.context ?: return false
    keepAppMedia(link)
    if (appMedia.get() != null) return false
    val next = intent(context, link?.url, false) ?: return false
    try {
        context.startActivity(next)
    } catch (exception: Exception) {
        Log.w(TAG, "Could not open the link ${link?.url}", exception)
    }
    return true
}

/**
 * Called at the start of LinkDispatcher.processing. Opens a share link where it leads, once its
 * redirect is known, or a video's post, once it is found, and returns true; returns false for every
 * other intent.
 */
@Export
fun dispatch(activity: Activity?, intent: Intent?): Boolean {
    if (activity == null || intent == null) return false
    val link = intent.data ?: return false
    if (intent.getBooleanExtra(EXTRA_SHARE_LINK, false)) {
        Thread {
            val target = resolve(link)
            activity.runOnUiThread { open(activity, link, target) }
        }
            .start()
        return true
    }
    if (intent.getBooleanExtra(EXTRA_VIDEO_LINK, false)) {
        Thread {
            val post = videoPost(activity, link)
            activity.runOnUiThread { openVideo(activity, link, post) }
        }
            .start()
        return true
    }
    return false
}

private fun openVideo(activity: Activity, link: Uri, post: String?) {
    try {
        val next =
            post?.let {
                Intent(activity, DetailActivity::class.java)
                    .putExtra(DetailActivity.EXTRA_LINKID, it)
            } ?: Intent(activity, BrowserActivity::class.java).setData(link)
        activity.startActivity(next)
    } catch (exception: Exception) {
        Log.w(TAG, "Could not open the video $link", exception)
    }
    activity.finish()
}

/**
 * The ID of the post a v.redd.it link was posted in, or null. Reddit's api/info finds the posts
 * with that link: the post itself, and the crossposts of it, which have no video of their own.
 */
private fun videoPost(context: Context, link: Uri): String? {
    try {
        // Built from the link's host: the e2e tests move a constant "https://v.redd.it/" to their
        // fake Reddit.
        val video = "https://" + link.host + "/" + link.pathSegments.first()
        val answer = apiGet(context, "api/info", mapOf("url" to video, "raw_json" to "1"))
        val children = JSONObject(answer).getJSONObject("data").getJSONArray("children")
        var crosspostOf: String? = null
        for (index in 0 until children.length()) {
            val post = children.getJSONObject(index).optJSONObject("data") ?: continue
            val parent = post.optString("crosspost_parent")
            if (parent.isEmpty() || post.isNull("crosspost_parent")) {
                return post.optString("id").takeIf { it.isNotEmpty() }
            }
            if (crosspostOf == null) crosspostOf = parent.removePrefix("t3_")
        }
        if (crosspostOf == null) Log.w(TAG, "Reddit knows no post with the video $link")
        return crosspostOf
    } catch (exception: Exception) {
        Log.w(TAG, "Could not find the post of the video $link", exception)
        return null
    }
}

/**
 * A GET to Reddit's API through the app's own request code, RedditApi.httpRequest. With a login, it
 * calls oauth.reddit.com with the session's token, refreshed when needed, and the app's user agent.
 * Reddit blocks other requests from apps.
 */
private fun apiGet(context: Context, path: String, params: Map<String, String>): String {
    val token = SessionManager.getToken()
    val httpRequest =
        RedditApi::class
            .java
            .getDeclaredMethod(
                "httpRequest",
                Context::class.java,
                RequestMethod::class.java,
                String::class.java,
                Map::class.java,
                SessionToken::class.java,
            )
    httpRequest.isAccessible = true
    // Without a login the app calls www.reddit.com, which needs the .json suffix.
    val fullPath = if (token == null) "$path.json" else path
    return httpRequest.invoke(null, context, RequestMethod.GET, fullPath, HashMap(params), token)
        as String
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
