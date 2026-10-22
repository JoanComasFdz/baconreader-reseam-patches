// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.settings

import app.reseam.patch.settings.SettingsGroup
import app.reseam.patch.settings.toggle

object ChatSettings : SettingsGroup(BaconReaderStrings) {
    val checkChatMessages by toggle(default = true, key = "reddit_chat.check_chat_messages")
}
