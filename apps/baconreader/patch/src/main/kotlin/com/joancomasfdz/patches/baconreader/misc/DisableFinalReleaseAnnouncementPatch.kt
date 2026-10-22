// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.misc

import app.reseam.patch.alwaysReturn
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM

// BaconReader's last release opens with a "FINAL RELEASE ANNOUNCEMENT!" popup saying the app stops
// working on July 1, 2023. The patches keep it working, and a fresh install of a patched app shows
// the popup again.
val disableFinalReleaseAnnouncement =
    patch("Disable final release announcement") {
        description(
            "Disables the \"FINAL RELEASE ANNOUNCEMENT!\" popup that says goodbye when the app starts."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)

        execute { showAnnouncement.alwaysReturn() }
    }

private val showAnnouncement =
    klass("com.onelouder.baconreader.ui.main.MainActivity").method("showWhatsNew") {
        strings("FINAL RELEASE ANNOUNCEMENT!")
    }
