// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.common

import android.content.Context
import app.reseam.runtime.Reseam
import app.reseam.runtime.ReseamBundle
import org.json.JSONObject

/** This bundle in the app runtime. */
object BaconReaderPatches {
    /** The bundle's name in `manifest.toml`. */
    const val NAME = "baconreader-patches"

    /** Fails until the settings host's app runtime has started. */
    fun bundle(): ReseamBundle = Reseam.bundle(NAME)

    /**
     * The bundle's version and the engine's, such as "0.1.1 (Reseam 0.28.0)". The settings host
     * writes them into the app's assets; a bundle built without a release tag has no version.
     */
    fun version(context: Context): String =
        try {
            val path = "reseam/$NAME/settings.json"
            val json = JSONObject(context.assets.open(path).bufferedReader().use { it.readText() })
            val bundle = json.getJSONObject("bundle")
            val version = if (bundle.isNull("version")) "unknown" else bundle.getString("version")
            "$version (Reseam ${json.optString("engine")})"
        } catch (exception: Exception) {
            "unknown"
        }
}
