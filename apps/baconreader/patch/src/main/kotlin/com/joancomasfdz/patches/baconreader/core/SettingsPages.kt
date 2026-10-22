// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.core

import app.reseam.patch.settings.SettingsPage
import com.joancomasfdz.baconreader.settings.BaconReaderStrings

/** The pages of the settings screen, in ReVanced's order. A page shows once a patch fills it. */
object SettingsPages {
    val Images = SettingsPage("images", BaconReaderStrings.pageImages, order = 10)
    val Links = SettingsPage("links", BaconReaderStrings.pageLinks, order = 20)
    val Comments = SettingsPage("comments", BaconReaderStrings.pageComments, order = 30)
    val Chat = SettingsPage("chat", BaconReaderStrings.pageChat, order = 40)
}
