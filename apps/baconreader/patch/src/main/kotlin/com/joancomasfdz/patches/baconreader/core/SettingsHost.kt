// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.core

import app.reseam.patch.settings.settingsHost
import com.joancomasfdz.baconreader.settings.BaconReaderStrings

/**
 * The patches' settings screen. Patches add their settings with `settings(baconReaderSettings,
 * ...)`.
 */
val baconReaderSettings =
    settingsHost("baconreader") {
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        brand(baconReaderBrand)
        strings(BaconReaderStrings)
    }
