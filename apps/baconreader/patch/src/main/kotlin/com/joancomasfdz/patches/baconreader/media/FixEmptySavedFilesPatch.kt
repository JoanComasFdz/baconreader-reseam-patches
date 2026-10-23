// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.media

import app.reseam.patch.Type
import app.reseam.patch.before
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import com.joancomasfdz.baconreader.savedfiles.bindings.FixEmptySavedFiles
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings

private const val SAVE_IMAGE_SERVICE = "com.onelouder.baconreader.imageutils.SaveImageService"

// Saving media creates "<title>.jpg" first, then downloads to "<title>.<extension of the URL>", so
// every save of media whose URL doesn't end in .jpg leaves an empty file next to the saved one.
val fixEmptySavedFiles =
    patch("Fix empty saved files") {
        description("Fixes saving media to the device also creating an empty file.")
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        dependsOn(baconReaderSettings)

        execute {
            // Before each download, the first file is removed if it is another, empty file, and the
            // "saved" notification uses the downloaded one.
            downloadToFile.before {
                thisObject.set(
                    completeFile,
                    FixEmptySavedFiles.onDownloadTarget(thisObject.field(completeFile), param(1)),
                )
            }
        }
    }

// Downloads a URL into a file: getSingleFile(url, file).
private val downloadToFile =
    klass(SAVE_IMAGE_SERVICE).method("getSingleFile") { returns(Type.Boolean) }

// The file the app reports as saved.
private val completeFile = klass(SAVE_IMAGE_SERVICE).field("completeFile")
