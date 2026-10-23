// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.core

import app.reseam.patch.appEntry
import app.reseam.patch.settings.settingsHost
import com.joancomasfdz.baconreader.common.bindings.ErrorDialog
import com.joancomasfdz.baconreader.settings.BaconReaderStrings

/**
 * The patches' settings screen and app runtime. Patches add their settings with
 * `settings(baconReaderSettings, ...)`, and depend on it to report errors with ErrorDialog.
 */
val baconReaderSettings =
    settingsHost("baconreader") {
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        brand(baconReaderBrand)
        strings(BaconReaderStrings)

        // The error dialog follows the screen on top from the start, to show errors on it.
        install { appEntry { ErrorDialog.init(application) } }
    }
