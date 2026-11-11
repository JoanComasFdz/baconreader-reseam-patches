// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.media

import app.reseam.patch.after
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import com.joancomasfdz.baconreader.albums.bindings.SwipeThroughAlbums
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings

private const val REDDIT_GALLERY_VIEWER =
    "com.onelouder.baconreader.reddit_gallery.RedditGalleryViewer"
private const val IMGUR_GALLERY_VIEWER =
    "com.onelouder.baconreader.imgur_gallery.ImgurGalleryViewer"

// BaconReader's album viewers go to the next and previous image only with the small arrows at the
// screen's sides and the thumbnail strip: a swipe on the image does nothing. The extension makes a
// horizontal swipe tap the arrow on its side.
val swipeThroughAlbums =
    patch(BaconReaderStrings.patchSwipeThroughAlbums) {
        description(BaconReaderStrings.patchSwipeThroughAlbumsDescription)
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        dependsOn(baconReaderSettings)

        execute {
            // Once each viewer has its views.
            redditGalleryCreated.after {
                SwipeThroughAlbums.attachToImage(
                    thisObject.field(redditGalleryImage),
                    thisObject.field(redditGalleryBack),
                    thisObject.field(redditGalleryForward),
                )
            }
            imgurAlbumCreated.after {
                SwipeThroughAlbums.attachToImage(
                    thisObject.field(imgurAlbumImage),
                    thisObject.field(imgurAlbumBack),
                    thisObject.field(imgurAlbumForward),
                )
                SwipeThroughAlbums.attachToVideo(
                    thisObject.field(imgurAlbumPlayer),
                    thisObject.field(imgurAlbumBack),
                    thisObject.field(imgurAlbumForward),
                )
            }
        }
    }

// A Reddit gallery's viewer sets up its views: its image, its arrows and its thumbnail strip.
private val redditGalleryCreated = klass(REDDIT_GALLERY_VIEWER).method("createView")

private val redditGalleryImage = klass(REDDIT_GALLERY_VIEWER).field("imageView")

private val redditGalleryBack = klass(REDDIT_GALLERY_VIEWER).field("backButton")

private val redditGalleryForward = klass(REDDIT_GALLERY_VIEWER).field("forwardButton")

// An Imgur album's viewer sets up its views: its image, its video, its arrows and its thumbnail
// strip.
private val imgurAlbumCreated = klass(IMGUR_GALLERY_VIEWER).method("createView")

private val imgurAlbumImage = klass(IMGUR_GALLERY_VIEWER).field("imageView")

private val imgurAlbumPlayer = klass(IMGUR_GALLERY_VIEWER).field("playerView")

private val imgurAlbumBack = klass(IMGUR_GALLERY_VIEWER).field("backButton")

private val imgurAlbumForward = klass(IMGUR_GALLERY_VIEWER).field("forwardButton")
