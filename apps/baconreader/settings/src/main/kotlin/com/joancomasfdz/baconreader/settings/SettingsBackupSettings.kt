// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.settings

import app.reseam.patch.settings.SettingsGroup
import app.reseam.patch.settings.action

object SettingsBackupSettings : SettingsGroup(BaconReaderStrings) {
    val exportSettings by action(key = "settings_backup.export_settings")
    val importSettings by action(key = "settings_backup.import_settings")
}
