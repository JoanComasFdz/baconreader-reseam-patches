// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.settings

import app.reseam.patch.settings.Choice
import app.reseam.patch.settings.SettingsGroup
import app.reseam.patch.settings.choice
import app.reseam.patch.settings.text

object LoadMoreCommentsSettings : SettingsGroup(BaconReaderStrings) {
    /**
     * A number from 200 to 500; the extension clamps it, and falls back to 500 for anything else.
     */
    val commentLimit by text(default = "500", key = "load_more_comments.comment_limit")
    val autoLoadMoreComments by
        choice(
            default = "2",
            choices =
                listOf(
                    Choice("0", BaconReaderStrings.autoLoadMoreCommentsOff),
                    Choice("1", BaconReaderStrings.autoLoadMoreComments1),
                    Choice("2", BaconReaderStrings.autoLoadMoreComments2),
                    Choice("3", BaconReaderStrings.autoLoadMoreComments3),
                    Choice("4", BaconReaderStrings.autoLoadMoreComments4),
                    Choice("5", BaconReaderStrings.autoLoadMoreComments5),
                ),
            key = "load_more_comments.auto_load_more_comments",
        )
}
