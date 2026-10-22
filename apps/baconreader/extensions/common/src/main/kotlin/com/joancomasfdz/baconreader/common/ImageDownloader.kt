// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.common

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Downloads images to a cache folder and reports the progress, so a progress bar can show while
 * they load. Glide, which shows them, does not report download progress.
 */
object ImageDownloader {
    private const val TAG = "ImageDownloader"

    private const val CACHE_FOLDER = "patches_images"
    private const val CACHE_MAX_BYTES = 100L * 1024 * 1024
    private const val PROGRESS_INTERVAL_MS = 100L
    private const val TIMEOUT_MS = 15000

    interface Listener {
        /** [total] is the size of the image, or -1 when the server does not tell it. */
        fun onProgress(received: Long, total: Long)

        fun onDownloaded(file: File)

        fun onFailed()
    }

    private val downloader = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    // Downloads running, and the listeners waiting for them. Only used on the main thread.
    private val inProgress = HashMap<String, MutableList<Listener>>()

    /** The cached file of an image, or null when it is not downloaded yet. */
    fun cached(context: Context, url: String): File? = cacheFile(context, url).takeIf { it.isFile }

    /**
     * Downloads the image in the background. Call it on the main thread; [listener] is called on
     * it.
     */
    fun download(context: Context, url: String, listener: Listener) {
        val file = cacheFile(context, url)
        if (file.isFile) {
            listener.onDownloaded(file)
            return
        }

        // The comment can show again, scrolled back or refreshed, while its image downloads: the
        // new view follows the download already running.
        inProgress[url]?.let {
            it.add(listener)
            return
        }
        val listeners = mutableListOf(listener)
        inProgress[url] = listeners

        val all =
            object : Listener {
                override fun onProgress(received: Long, total: Long) {
                    listeners.forEach { it.onProgress(received, total) }
                }

                override fun onDownloaded(file: File) {
                    inProgress.remove(url)
                    listeners.forEach { it.onDownloaded(file) }
                }

                override fun onFailed() {
                    inProgress.remove(url)
                    listeners.forEach { it.onFailed() }
                }
            }
        downloader.execute {
            try {
                fetchWithRetry(url, file, all)
                file.setLastModified(System.currentTimeMillis())
                main.post { all.onDownloaded(file) }
                trimCache(file.parentFile)
            } catch (exception: Exception) {
                Log.i(TAG, "Could not download $url", exception)
                main.post { all.onFailed() }
            }
        }
    }

    /** Downloads, trying again after network errors and server errors. */
    private fun fetchWithRetry(url: String, file: File, listener: Listener) {
        var attempt = 1
        while (true) {
            try {
                fetch(url, file, listener)
                return
            } catch (exception: HttpStatusException) {
                // Missing or forbidden images do not appear by asking again.
                val retry =
                    exception.status >= 500 || exception.status == 408 || exception.status == 429
                if (!retry || attempt >= GlideUtils.MAX_ATTEMPTS) throw exception
            } catch (exception: IOException) {
                if (attempt >= GlideUtils.MAX_ATTEMPTS) throw exception
            }
            Log.i(TAG, "Download failed (attempt $attempt), trying again: $url")
            Thread.sleep(GlideUtils.RETRY_DELAY_MS * attempt)
            attempt++
        }
    }

    private class HttpStatusException(val status: Int) : Exception("HTTP $status")

    private fun fetch(url: String, file: File, listener: Listener) {
        val connection = URL(url).openConnection() as HttpURLConnection
        val partial = File(file.path + ".part")
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) throw HttpStatusException(status)

            val total = connection.contentLength.toLong()
            var received = 0L
            var lastReport = 0L
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        received += read
                        val now = SystemClock.uptimeMillis()
                        if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                            lastReport = now
                            val soFar = received
                            main.post { listener.onProgress(soFar, total) }
                        }
                    }
                }
            }
            if (!partial.renameTo(file)) throw IOException("Could not save $file")
        } finally {
            connection.disconnect()
            partial.delete()
        }
    }

    private fun cacheFile(context: Context, url: String): File {
        val folder = File(context.cacheDir, CACHE_FOLDER)
        folder.mkdirs()
        return File(folder, sha1(url))
    }

    /** Removes the oldest images while the cache is bigger than its limit. */
    @Synchronized
    private fun trimCache(folder: File?) {
        val files = folder?.listFiles() ?: return
        var size = files.sumOf { it.length() }
        if (size <= CACHE_MAX_BYTES) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (size <= CACHE_MAX_BYTES) break
            size -= file.length()
            file.delete()
        }
    }

    private fun sha1(text: String): String = buildString {
        for (byte in MessageDigest.getInstance("SHA-1").digest(text.toByteArray())) {
            append("%02x".format(byte))
        }
    }
}
