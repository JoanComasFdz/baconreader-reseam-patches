// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.common

import app.reseam.runtime.Reseam
import app.reseam.runtime.ReseamBundle

/** This bundle in the app runtime. */
object BaconReaderPatches {
    /** The bundle's name in `manifest.toml`. */
    const val NAME = "baconreader-patches"

    /** Fails until the settings host's app runtime has started. */
    fun bundle(): ReseamBundle = Reseam.bundle(NAME)
}
