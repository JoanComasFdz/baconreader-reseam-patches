// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("SaveVideosWithSound")

package com.joancomasfdz.baconreader.videosound

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import android.util.Xml
import androidx.documentfile.provider.DocumentFile
import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.common.ErrorDialog
import com.onelouder.baconreader.reddit.Link
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import org.xmlpull.v1.XmlPullParser

/*
 * Reddit videos (v.redd.it) keep the picture and the sound in separate files, which players
 * combine. "Save to device" downloads only the picture (fallback_url), so saved videos have no
 * sound. After the download, the audio track is downloaded too and both are combined into the saved
 * file, without re-encoding.
 */

private const val TAG = "SaveVideosWithSound"

/** Reddit's usual names of the audio file, for when the DASH manifest can't be read. Best first. */
private val AUDIO_NAMES =
    listOf(
        "CMAF_AUDIO_128.mp4",
        "CMAF_AUDIO_64.mp4",
        "DASH_AUDIO_128.mp4",
        "DASH_AUDIO_64.mp4",
        "DASH_audio.mp4",
    )
private const val BUFFER_SIZE = 4 * 1024 * 1024
private const val ROTATION = "rotation-degrees"

/**
 * Adds the sound to a saved Reddit video, after each download into the saved file, on the download
 * thread.
 */
@Export
fun onDownloaded(context: Context?, downloaded: Boolean, file: DocumentFile?, link: Link?) {
    if (!downloaded || context == null || file == null || link == null) return
    try {
        if (!link.isRedditVideo) return
        val video = link.media()?.redditVideo
        if (video == null || video.isGif) return
        val fallbackUrl = video.fallbackUrl ?: return
        val saved = file.uri ?: return

        val audioUrl = findAudio(video.dashUrl, fallbackUrl)
        if (audioUrl == null) {
            Log.i(TAG, "No audio track: the video has no sound")
            return
        }
        mux(context, saved, audioUrl)
    } catch (exception: Exception) {
        ErrorDialog.report(
            TAG,
            "Could not add the sound to the saved video, which was saved without it",
            exception,
        )
    }
}

/** The audio file of the video: from the DASH manifest, else Reddit's usual names that exist. */
private fun findAudio(dashUrl: String?, fallbackUrl: String): String? {
    val base = fallbackUrl.substring(0, fallbackUrl.lastIndexOf('/') + 1)
    if (dashUrl != null) {
        try {
            val manifest = String(download(dashUrl.replace("&amp;", "&")), Charsets.UTF_8)
            // Null: the video has no sound.
            val best = bestAudio(manifest) ?: return null
            return if (best.startsWith("http")) best else base + best
        } catch (exception: Exception) {
            Log.i(TAG, "Could not read the DASH manifest $dashUrl", exception)
        }
    }
    return AUDIO_NAMES.map { base + it }.firstOrNull(::exists)
}

/** The file of the audio rendition with the highest bandwidth in the DASH manifest, or null. */
private fun bestAudio(manifest: String): String? {
    val parser = Xml.newPullParser()
    parser.setInput(StringReader(manifest))
    var best: String? = null
    var bestBandwidth = -1L
    var audioSet = false
    var audio = false
    var bandwidth = -1L
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        if (event == XmlPullParser.START_TAG) {
            when (parser.name) {
                "AdaptationSet" ->
                    audioSet =
                        isAudio(parser.getAttributeValue(null, "contentType")) ||
                            isAudio(parser.getAttributeValue(null, "mimeType"))
                "Representation" -> {
                    val mimeType = parser.getAttributeValue(null, "mimeType")
                    audio = if (mimeType == null) audioSet else isAudio(mimeType)
                    bandwidth = parser.getAttributeValue(null, "bandwidth")?.toLong() ?: 0
                }
                "BaseURL" ->
                    if (audio && bandwidth > bestBandwidth) {
                        best = parser.nextText().trim()
                        bestBandwidth = bandwidth
                    }
            }
        }
        event = parser.next()
    }
    return best
}

/** "audio" (contentType) or "audio/mp4" (mimeType). */
private fun isAudio(type: String?): Boolean = type != null && type.startsWith("audio")

private fun exists(url: String): Boolean {
    var connection: HttpURLConnection? = null
    return try {
        connection = open(url)
        connection.requestMethod = "HEAD"
        connection.responseCode == HttpURLConnection.HTTP_OK
    } catch (exception: IOException) {
        false
    } finally {
        connection?.disconnect()
    }
}

/**
 * The saved video and the audio in one MP4, written over the saved file. The work files go to the
 * cache, as the muxer needs a real file.
 */
private fun mux(context: Context, saved: Uri, audioUrl: String) {
    val directory = File(context.cacheDir, "patches_video_audio")
    if (!directory.isDirectory && !directory.mkdirs()) throw IOException("No cache folder")
    val video = File(directory, "video.mp4")
    val audio = File(directory, "audio.mp4")
    val output = File(directory, "output.mp4")
    try {
        (context.contentResolver.openInputStream(saved)
                ?: throw IOException("Cannot read the saved video $saved"))
            .use { input -> FileOutputStream(video).use { copy(input, it) } }
        FileOutputStream(audio).use { it.write(download(audioUrl)) }

        val muxer = MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val extractors = mutableListOf<MediaExtractor>()
        val tracks = mutableListOf<Int>()
        try {
            for ((source, mimePrefix) in listOf(video to "video/", audio to "audio/")) {
                val extractor = MediaExtractor()
                extractors.add(extractor)
                extractor.setDataSource(source.path)
                val track = selectTrack(extractor, mimePrefix)
                if (track < 0) throw IOException("No $mimePrefix track in $source")
                val format = extractor.getTrackFormat(track)
                if (format.containsKey(ROTATION)) {
                    muxer.setOrientationHint(format.getInteger(ROTATION))
                }
                tracks.add(muxer.addTrack(format))
            }
            muxer.start()
            val buffer = ByteBuffer.allocate(BUFFER_SIZE)
            val info = MediaCodec.BufferInfo()
            for ((i, extractor) in extractors.withIndex()) {
                while (true) {
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = extractor.sampleTime
                    info.flags =
                        if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                            MediaCodec.BUFFER_FLAG_KEY_FRAME
                        } else {
                            0
                        }
                    muxer.writeSampleData(tracks[i], buffer, info)
                    extractor.advance()
                }
            }
            muxer.stop()
        } finally {
            extractors.forEach { it.release() }
            muxer.release()
        }

        // "wt": the saved file is replaced, not appended to.
        FileInputStream(output).use { input ->
            (context.contentResolver.openOutputStream(saved, "wt")
                    ?: throw IOException("Cannot write the saved video $saved"))
                .use { copy(input, it) }
        }
    } finally {
        video.delete()
        audio.delete()
        output.delete()
    }
}

private fun selectTrack(extractor: MediaExtractor, mimePrefix: String): Int {
    for (i in 0 until extractor.trackCount) {
        val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
        if (mime != null && mime.startsWith(mimePrefix)) {
            extractor.selectTrack(i)
            return i
        }
    }
    return -1
}

private fun open(url: String): HttpURLConnection {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 20000
    connection.readTimeout = 30000
    connection.setRequestProperty(
        "User-Agent",
        "android:com.onelouder.baconreader (BaconReader patches)",
    )
    return connection
}

private fun download(url: String): ByteArray {
    val connection = open(url)
    try {
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code for $url")
        connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            copy(input, output)
            return output.toByteArray()
        }
    } finally {
        connection.disconnect()
    }
}

private fun copy(input: InputStream, output: OutputStream) {
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        output.write(buffer, 0, read)
    }
}
