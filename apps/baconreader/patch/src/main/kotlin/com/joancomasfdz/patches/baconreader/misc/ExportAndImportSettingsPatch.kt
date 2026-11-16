// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.misc

import app.reseam.patch.patch
import app.reseam.patch.settings.onAction
import app.reseam.patch.settings.section
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.joancomasfdz.baconreader.settings.SettingsBackupSettings
import com.joancomasfdz.baconreader.settingsbackup.bindings.SettingsBackup
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings

// BaconReader keeps its settings only on the device: a new phone or a reinstall starts from the
// defaults, and each setting has to be found and set again, the patches' too.
val exportAndImportSettings =
    patch(BaconReaderStrings.patchExportAndImportSettings) {
        description(BaconReaderStrings.patchExportAndImportSettingsDescription)
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        settings(
            baconReaderSettings,
            section(
                BaconReaderStrings.sectionSettingsBackup,
                SettingsBackupSettings.exportSettings,
                SettingsBackupSettings.importSettings,
            ),
        )

        execute {
            onAction(SettingsBackupSettings.exportSettings) {
                SettingsBackup.exportSettings(activity)
            }
            onAction(SettingsBackupSettings.importSettings) {
                SettingsBackup.importSettings(activity)
            }
        }
    }
