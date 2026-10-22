// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.settings

import app.reseam.patch.settings.SettingsGroup
import app.reseam.patch.settings.toggle

object EmbedCommentImagesSettings : SettingsGroup(BaconReaderStrings) {
    val showImages by toggle(default = true, key = "embed_comment_images.show_images")
    val imagesFullWidth by toggle(default = false, key = "embed_comment_images.images_full_width")
    val imagesLongPressDownload by
        toggle(default = true, key = "embed_comment_images.images_long_press_download")
    val imagesWifiOnly by toggle(default = false, key = "embed_comment_images.images_wifi_only")
    val showVideos by toggle(default = true, key = "embed_comment_images.show_videos")
    val youtubeTitles by toggle(default = true, key = "embed_comment_images.youtube_titles")
    val youtubeThumbnails by
        toggle(default = false, key = "embed_comment_images.youtube_thumbnails")
    val xPosts by toggle(default = true, key = "embed_comment_images.x_posts")
    val blueskyPosts by toggle(default = true, key = "embed_comment_images.bluesky_posts")
}
