// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.media

import app.reseam.patch.after
import app.reseam.patch.before
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.point
import app.reseam.patch.reserveLocal
import com.joancomasfdz.baconreader.thumbnails.bindings.FixListThumbnails
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM

private const val LINK = "com.onelouder.baconreader.reddit.Link"
private const val LINK_HOLDER = "com.onelouder.baconreader.adapters.LinkHolder"
private const val DETAIL_LINK_FRAGMENT = "com.onelouder.baconreader.adapters.DetailLinkFragment"

// Reddit sends list thumbnails as signed preview URLs whose "&" arrive HTML-escaped, since
// BaconReader doesn't ask for raw JSON. BaconReader loads them as they are, the server answers 403,
// and the thumbnail stays empty. It also loads each thumbnail once, so a network hiccup leaves it
// empty, and shows no thumbnail for link posts, NSFW posts or crossposted galleries.
val fixListThumbnails =
    patch("Fix list thumbnails") {
        description(
            "Fixes empty thumbnails in the post list, shows a preview as thumbnail for link posts and crossposted galleries, and blurs the thumbnails of NSFW and spoiler posts."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)

        execute {
            // Checked before every list and widget thumbnail. A blurred NSFW thumbnail shows even
            // where the app would hide it.
            canShowThumbnail.before {
                FixListThumbnails.fixThumbnail(param(0))
                whenTrue(FixListThumbnails.forceShow(param(0))) { returnTrue() }
            }

            // The method overwrites its Link parameter with the Imgur URL of Imgur posts.
            val link = loadThumbnail.reserveLocal("link", LINK)
            loadThumbnail.before {
                local(link).assign(param(1))
                FixListThumbnails.fixThumbnail(param(1))
                FixListThumbnails.beforeLoad(param(1), param(2))
            }
            thumbnailInto.before {
                FixListThumbnails.loadThumbnail(capture("request"), capture("view"), local(link))
                returnVoid()
            }

            // Crossposts of galleries are not marked as galleries: the crossposted post is checked
            // too.
            listGallery.after {
                capture("gallery").assign(FixListThumbnails.isGallery(thisObject.field(listLink)))
            }
            headerGallery.after {
                capture("gallery").assign(FixListThumbnails.isGallery(thisObject.field(headerLink)))
            }
        }
    }

private val canShowThumbnail =
    klass("com.onelouder.baconreader.utils.Utils").method("canShowThumbnail") { params(LINK) }

private val loadThumbnail =
    klass("com.onelouder.baconreader.LinkProcessor").method("loadThumbnail") {
        params("android.content.Context", LINK, "android.widget.ImageView")
    }

private val thumbnailInto =
    loadThumbnail
        .point("thumbnailInto") {
            invokeVirtual {
                name("into")
                params("android.widget.ImageView")
            }
        }
        .captureArgumentAs("request", 0)
        .captureArgumentAs("view", 1)

private val listLink = klass(LINK_HOLDER).field("link")
private val headerLink = klass(DETAIL_LINK_FRAGMENT).field("link")

// Link.isGallery decides whether the post list shows the app's gallery indicator.
private val listGallery =
    klass(LINK_HOLDER)
        .method("updateView") { params("java.lang.Object") }
        .point("listGallery") {
            field {
                owner(LINK)
                name("isGallery")
            }
        }
        .captureAs("gallery")

// The same for the post header.
private val headerGallery =
    klass(DETAIL_LINK_FRAGMENT)
        .method("resetHeader") { params() }
        .point("headerGallery") {
            field {
                owner(LINK)
                name("isGallery")
            }
        }
        .captureAs("gallery")
