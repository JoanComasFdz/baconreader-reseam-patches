// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.settings

import app.reseam.patch.settings.Choice
import app.reseam.patch.settings.SettingsGroup
import app.reseam.patch.settings.choice
import app.reseam.patch.settings.toggle

object UserAvatarsSettings : SettingsGroup(BaconReaderStrings) {
    val userAvatars by toggle(default = false, key = "user_avatars.user_avatars")
    val userAvatarSize by
        choice(
            default = "0",
            choices =
                listOf(
                    Choice("0", BaconReaderStrings.userAvatarSizeNormal),
                    Choice("1", BaconReaderStrings.userAvatarSizeMedium),
                    Choice("2", BaconReaderStrings.userAvatarSizeLarge),
                    Choice("3", BaconReaderStrings.userAvatarSizeExtraLarge),
                ),
            key = "user_avatars.user_avatar_size",
        )
    val userAvatarBackground by toggle(default = true, key = "user_avatars.user_avatar_background")
}
