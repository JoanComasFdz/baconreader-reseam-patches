// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.settings

import app.reseam.patch.settings.SettingsGroup
import app.reseam.patch.settings.toggle

object DetailVideoSettings : SettingsGroup(BaconReaderStrings) {
    val autoplayVideosInDetailView by
        toggle(default = true, key = "detail_video.autoplay_videos_in_detail_view")
}
