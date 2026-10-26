// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.comments

import app.reseam.patch.MethodTarget
import app.reseam.patch.PointTarget
import app.reseam.patch.Type
import app.reseam.patch.after
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.point
import app.reseam.patch.settings.gate
import app.reseam.patch.settings.section
import com.joancomasfdz.baconreader.embedcommentimages.bindings.EmbedCommentImages
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.joancomasfdz.baconreader.settings.EmbedCommentImagesSettings
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.SettingsPages
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings
import com.joancomasfdz.patches.baconreader.media.fixImgurApi

private const val COMMENT_HOLDER = "com.onelouder.baconreader.adapters.CommentHolder"
private const val BUFFER_TYPE = "android.widget.TextView\$BufferType"

// BaconReader shows images in comments and posts as links, and GIFs as a "GIF" link. Reddit's
// emotes show as their raw <img> tags, and videos uploaded in comments link to a Reddit page that
// doesn't play them. Imgur albums are listed with the app's Imgur client, which needs Fix Imgur
// API.
val embedCommentImages =
    patch("Embed comment images") {
        description(
            "Shows images, GIFs, emotes, Imgur albums and videos in comments and posts, YouTube links with their title and thumbnail, and X and Bluesky links as the post."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        dependsOn(fixImgurApi)
        settings(
            baconReaderSettings,
            section(
                SettingsPages.Images,
                BaconReaderStrings.sectionEmbedCommentImages,
                EmbedCommentImagesSettings.showImages,
                EmbedCommentImagesSettings.imagesFullWidth,
                EmbedCommentImagesSettings.imagesLongPressDownload,
                EmbedCommentImagesSettings.imagesWifiOnly,
                EmbedCommentImagesSettings.showVideos,
            ),
            section(
                SettingsPages.Links,
                BaconReaderStrings.sectionEmbedCommentImages,
                EmbedCommentImagesSettings.youtubeTitles,
                EmbedCommentImagesSettings.youtubeThumbnails,
                EmbedCommentImagesSettings.xPosts,
                EmbedCommentImagesSettings.blueskyPosts,
            ),
        )

        execute {
            // The views the images are shown in are made in code: these ids name them, a GIF's
            // apart from an image's, so the e2e tests find them.
            resources.addId("baconreader_patches_comment_image")
            resources.addId("baconreader_patches_comment_gif")

            // The images are shown instead of the "GIF" link the app puts in place of the <img>.
            gate(EmbedCommentImagesSettings.showImages) { handleGifLink.alwaysReturn() }

            // Reddit's API sends the same placeholder for every emote, so the real ones are found
            // in the comment's markdown.
            commentBodyText.after {
                EmbedCommentImages.embedComment(
                    capture("textView"),
                    param(0).field(messageBody),
                )
            }
            postText.after { EmbedCommentImages.embed(capture("textView")) }
            // Card mode shows the post text in a list, one TextView per paragraph: YouTube titles
            // and thumbnails are drawn in the text, but there is no room for images under it, so
            // they stay links.
            cardPostText.after { EmbedCommentImages.embed(capture("textView")) }
            // Image, GIF and video posts show their text under the media.
            mediaPostText.after { EmbedCommentImages.embed(capture("textView")) }
        }
    }

private val handleGifLink = klass(COMMENT_HOLDER).method("handleGifLinkInComment")

private val messageBody = klass("com.onelouder.baconreader.reddit.Message").field("body")

// The comment's own text, set as spannable; the other setText calls set its header and footer.
private val commentBodyText =
    klass(COMMENT_HOLDER)
        .method("updateView")
        .point("commentBodyText") {
            invokeVirtual {
                owner("android.widget.TextView")
                name("setText")
                params(Type.CharSequence, BUFFER_TYPE)
            }
        }
        .captureArgumentAs("textView", 0)

private val postText =
    klass("com.onelouder.baconreader.adapters.DetailLinkFragment")
        .method("updateSelftext")
        .setText("postText", "android.widget.TextView")

private val cardPostText =
    klass("com.onelouder.baconreader.adapters.SelftextAdapter")
        .method("getView")
        .setText("cardPostText", "android.widget.TextView")

// The post's text is an ExpandableTextView; the other setText call shows load errors.
private val mediaPostText =
    klass("com.onelouder.baconreader.PostMediaHandler")
        .method("<init>")
        .setText("mediaPostText", "com.onelouder.baconreader.ExpandableTextView")

/**
 * The method's TextView.setText(CharSequence) call on a [textView] class, with the view captured.
 */
private fun MethodTarget.setText(debugName: String, textView: String): PointTarget =
    point(debugName) {
            invokeVirtual {
                owner(textView)
                name("setText")
                params(Type.CharSequence)
            }
        }
        .captureArgumentAs("textView", 0)
