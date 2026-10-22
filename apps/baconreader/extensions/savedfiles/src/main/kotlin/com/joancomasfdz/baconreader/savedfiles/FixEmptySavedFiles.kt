// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("FixEmptySavedFiles")

package com.joancomasfdz.baconreader.savedfiles

import androidx.documentfile.provider.DocumentFile
import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.common.ErrorDialog

private const val TAG = "FixEmptySavedFiles"

/**
 * Called before the media is downloaded to [target]. Saving a post's media first creates the file
 * the app will report as saved, [placeholder], named after the post with a guessed extension
 * ("title.jpg"). The download then uses the extension of the media's URL ("title.jpeg"), in a
 * second file, and the first one stays empty. Deletes the placeholder when it is another, empty
 * file, and returns the file the app should report as saved.
 */
@Export
fun onDownloadTarget(placeholder: DocumentFile?, target: DocumentFile?): DocumentFile? {
    if (placeholder == null || target == null || placeholder === target) return placeholder
    try {
        val placeholderUri = placeholder.uri ?: return placeholder
        if (placeholderUri == target.uri) return placeholder
        // Albums keep a folder here; only an empty file is a leftover placeholder.
        if (!placeholder.isFile || placeholder.length() != 0L) return placeholder

        placeholder.delete()
        return target
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not remove the empty file", exception)
        return placeholder
    }
}
