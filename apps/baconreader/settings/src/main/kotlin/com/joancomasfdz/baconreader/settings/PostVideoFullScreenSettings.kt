// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.settings

import app.reseam.patch.settings.SettingsGroup
import app.reseam.patch.settings.toggle

object PostVideoFullScreenSettings : SettingsGroup(BaconReaderStrings) {
    val postVideoFullScreenButton by
        toggle(default = true, key = "post_video_full_screen.full_screen_button")
}
