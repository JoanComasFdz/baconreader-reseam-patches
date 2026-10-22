// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.common

import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.HttpException
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import java.io.File
import java.util.WeakHashMap

/** Loads images with the Glide that BaconReader bundles, trying again when loading fails. */
object GlideUtils {
    private const val TAG = "GlideUtils"

    /** Attempts to load an image before it is reported as failed. */
    const val MAX_ATTEMPTS = 3

    /** Delay before the second attempt; it grows with each attempt. */
    const val RETRY_DELAY_MS = 1000L

    interface LoadCallback {
        fun onReady()

        fun onFailed()
    }

    /** Retrying only helps when the failure was not a permanent one. */
    private interface RequestCallback {
        fun onReady()

        fun onFailed(retry: Boolean)
    }

    // The last request of each view, so a retry does not replace what the view shows now.
    private val latest = WeakHashMap<View, Any>()

    /**
     * The request manager of the view's activity. Glide.with(View) would use the fragment showing
     * the view when it is attached, and the requests of a fragment Glide considers stopped never
     * run.
     */
    fun with(view: View): RequestManager = Glide.with(view.context)

    /**
     * Loads an image into the view. [thumbnailUrl] is shown until the image is loaded. [callback]
     * is called when the image is ready or failed to load.
     */
    fun load(imageView: ImageView, url: String, thumbnailUrl: String?, callback: LoadCallback?) {
        load(imageView, { it.load(url) }, thumbnailUrl, callback, Any(), 1)
    }

    /** Loads a downloaded image into the view, like [load] with a URL. */
    fun load(imageView: ImageView, file: File, thumbnailUrl: String?, callback: LoadCallback?) {
        load(imageView, { it.load(file) }, thumbnailUrl, callback, Any(), 1)
    }

    private fun load(
        imageView: ImageView,
        model: (RequestManager) -> RequestBuilder<Drawable>,
        thumbnailUrl: String?,
        callback: LoadCallback?,
        request: Any,
        attempt: Int,
    ) {
        latest[imageView] = request
        val manager = with(imageView)
        var builder = model(manager)
        if (thumbnailUrl != null) builder = builder.thumbnail(manager.load(thumbnailUrl))
        builder
            .listener(
                listener(
                    object : RequestCallback {
                        override fun onReady() {
                            callback?.onReady()
                        }

                        override fun onFailed(retry: Boolean) {
                            // Try again, unless the view shows something else now.
                            if (retry && attempt < MAX_ATTEMPTS && latest[imageView] === request) {
                                imageView.postDelayed(
                                    {
                                        if (latest[imageView] !== request) return@postDelayed
                                        try {
                                            load(
                                                imageView,
                                                model,
                                                thumbnailUrl,
                                                callback,
                                                request,
                                                attempt + 1,
                                            )
                                        } catch (exception: Exception) {
                                            Log.i(TAG, "Could not load the image again", exception)
                                            callback?.onFailed()
                                        }
                                    },
                                    RETRY_DELAY_MS * attempt,
                                )
                                return
                            }
                            callback?.onFailed()
                        }
                    }
                )
            )
            .into(imageView)
    }

    /**
     * Loads a request the app built into the view, trying again when it fails. [onFailed] runs when
     * the last attempt fails.
     */
    fun intoWithRetry(
        requestBuilder: RequestBuilder<*>?,
        imageView: ImageView?,
        onFailed: Runnable? = null,
    ) {
        if (requestBuilder == null || imageView == null) return
        @Suppress("UNCHECKED_CAST")
        intoWithRetry(requestBuilder as RequestBuilder<Any>, imageView, Any(), 1, onFailed)
    }

    private fun intoWithRetry(
        requestBuilder: RequestBuilder<Any>,
        imageView: ImageView,
        request: Any,
        attempt: Int,
        onFailed: Runnable?,
    ) {
        try {
            latest[imageView] = request
            requestBuilder
                .listener(
                    listener(
                        object : RequestCallback {
                            override fun onReady() {}

                            override fun onFailed(retry: Boolean) {
                                if (latest[imageView] !== request) return
                                if (!retry || attempt >= MAX_ATTEMPTS) {
                                    onFailed?.run()
                                    return
                                }
                                imageView.postDelayed(
                                    {
                                        if (latest[imageView] === request) {
                                            intoWithRetry(
                                                requestBuilder,
                                                imageView,
                                                request,
                                                attempt + 1,
                                                onFailed,
                                            )
                                        }
                                    },
                                    RETRY_DELAY_MS * attempt,
                                )
                            }
                        }
                    )
                )
                .into(imageView)
        } catch (exception: Exception) {
            Log.i(TAG, "Could not load the image", exception)
        }
    }

    private fun <T> listener(callback: RequestCallback): RequestListener<T> =
        object : RequestListener<T> {
            override fun onResourceReady(
                resource: T,
                model: Any?,
                target: Target<T>?,
                dataSource: DataSource?,
                isFirstResource: Boolean,
            ): Boolean {
                callback.onReady()
                // Glide still sets the image.
                return false
            }

            override fun onLoadFailed(
                error: GlideException?,
                model: Any?,
                target: Target<T>?,
                isFirstResource: Boolean,
            ): Boolean {
                Log.i(TAG, "Could not load $model: $error")
                callback.onFailed(isRetryable(error))
                return false
            }
        }

    /**
     * Loads an image at the given size, trying again when it fails. Blocks: call it off the main
     * thread.
     */
    fun fetch(view: View, url: String, width: Int, height: Int): Drawable {
        var attempt = 1
        while (true) {
            try {
                return with(view).load(url).submit(width, height).get()
            } catch (exception: Exception) {
                if (attempt >= MAX_ATTEMPTS || !isRetryable(exception.cause)) throw exception
                Log.i(TAG, "Could not load $url (attempt $attempt), trying again")
                Thread.sleep(RETRY_DELAY_MS * attempt)
                attempt++
            }
        }
    }

    /**
     * Whether loading again could help: for network trouble and server errors, not when the server
     * answered that the image is not there or not allowed, as for a missing or expired image.
     * [error] is the GlideException of the failed load, or null.
     */
    fun isRetryable(error: Throwable?): Boolean {
        if (error !is GlideException) return true
        val causes = error.rootCauses
        if (causes.isEmpty()) return true
        return causes.any { cause ->
            if (cause !is HttpException) return@any true
            val code = cause.statusCode
            code < 400 || code >= 500 || code == 408 || code == 429
        }
    }

    /** Cancels the view's request and frees its image. */
    fun clear(view: View) {
        latest.remove(view)
        try {
            with(view).clear(view)
        } catch (exception: IllegalArgumentException) {
            // Glide refuses requests for a destroyed activity, which has nothing left to clear.
        }
    }
}
