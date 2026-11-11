// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.settings

import app.reseam.patch.settings.SettingsGroup
import app.reseam.patch.settings.toggle

object LinkSettings : SettingsGroup(BaconReaderStrings) {
    val openWebPagesInBrowser by toggle(default = false, key = "links.open_web_pages_in_browser")
}
