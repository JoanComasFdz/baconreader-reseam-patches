// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.settings

import app.reseam.patch.settings.SettingsGroup
import app.reseam.patch.settings.toggle

object AccountColorsSettings : SettingsGroup(BaconReaderStrings) {
    val accountColors by toggle(default = true, key = "account_colors.account_colors")
}
