// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("SettingsEntry")

package com.joancomasfdz.baconreader.settingsentry

import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.settings.BaconReaderStrings

/** BaconReader's settings sections, then the patches' entry. */
@Export
fun withEntry(sections: Array<String?>?): Array<String?> =
    (sections ?: emptyArray()) + BaconReaderStrings.settingsEntry

/** Whether the menu position is the patches' entry, which comes after BaconReader's sections. */
@Export fun isEntry(sections: Array<String?>?, position: Int): Boolean = position == sections?.size
