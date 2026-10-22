// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.settings

import app.reseam.patch.settings.SettingsGroup
import app.reseam.patch.settings.text
import app.reseam.patch.settings.toggle

object PostMediaSettings : SettingsGroup(BaconReaderStrings) {
    val linkPreviewJavascript by toggle(default = true, key = "post_media.link_preview_javascript")
    val linkPreviewJavascriptBlocked by
        text(default = "", multiline = true, key = "post_media.link_preview_javascript_blocked")

    // BaconReader's own list, without the sites that shut down, and with Bluesky and x.com.
    val linkPreviewJavascriptAllowed by
        text(
            default =
                "bsky.app\nimgur.com\ninstagram.com\nreddit.com\nstreamable.com\ntwitch.tv\ntwitter.com\nvimeo.com\nx.com\nyoutu.be\nyoutube.com",
            multiline = true,
            key = "post_media.link_preview_javascript_allowed",
        )
}
