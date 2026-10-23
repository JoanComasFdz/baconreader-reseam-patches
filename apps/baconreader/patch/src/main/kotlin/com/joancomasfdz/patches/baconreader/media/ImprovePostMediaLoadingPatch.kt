// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.media

import app.reseam.patch.Type
import app.reseam.patch.after
import app.reseam.patch.before
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.point
import app.reseam.patch.settings.section
import com.joancomasfdz.baconreader.postmedia.bindings.PostMedia
import com.joancomasfdz.baconreader.postmedia.bindings.RedditGallery
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.joancomasfdz.baconreader.settings.PostMediaSettings
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.SettingsPages
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings

private const val POST_MEDIA_HANDLER = "com.onelouder.baconreader.PostMediaHandler"
private const val GALLERY_VIEWER = "com.onelouder.baconreader.reddit_gallery.RedditGalleryViewer"
private const val IMAGE_HELPER = "com.onelouder.baconreader.imageutils.ImageHelper"

// BaconReader shows only a spinner while post media loads, with no progress, and keeps it when
// loading fails. Galleries download a full image for every thumbnail of their strip. A deleted
// RedGIFs video shows RedGIFs' web page, which needs JavaScript, and the web view of link posts
// runs JavaScript only for a list of sites from before most of today's web needed it, several of
// which shut down, so many pages show nothing.
val improvePostMediaLoading =
    patch("Improve post media loading") {
        description(
            "Shows a still preview with a progress bar and the downloaded size while post media and gallery images load, uses small previews for the thumbnails of galleries, shows a message instead of a broken page when a RedGIFs video was deleted, and adds settings for which link post pages run JavaScript."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        settings(
            baconReaderSettings,
            section(
                SettingsPages.Links,
                BaconReaderStrings.sectionPostMedia,
                PostMediaSettings.linkPreviewJavascript,
                PostMediaSettings.linkPreviewJavascriptBlocked,
                PostMediaSettings.linkPreviewJavascriptAllowed,
            ),
        )

        execute {
            // The loading overlay: still preview, size and progress, before the app resolves the
            // media.
            start.before {
                PostMedia.onStart(
                    thisObject,
                    thisObject.field(handlerLink),
                    thisObject.field(handlerFlipper),
                    thisObject.field(handlerVideo),
                )
            }

            // When Glide fails, the app downloads the image with OkHttp. If that fails too, it only
            // logs it and keeps the loading page: the bar shows the failure.
            gifFallbackFailures.forEach {
                it.before {
                    PostMedia.onImageLoadFailed(
                        thisObject.field(gifFallbackListener).field(glideListenerHandler)
                    )
                }
            }

            // GIFs load through the app's cache loader, which reports the download progress,
            // instead of Glide.
            loadGifContent.before {
                whenTrue(PostMedia.loadsWithProgress(param(0))) {
                    // displayGif() plays resolvedUrl from the cache when the download ends, and
                    // loadGifContent is sometimes called with a rewritten preview URL.
                    thisObject.set(handlerResolvedUrl, param(0))
                    thisObject.call(showPage, int(0))
                    thisObject.call(loadToCache, param(0))
                    returnVoid()
                }
            }
            receivedData.before {
                PostMedia.onProgress(thisObject.field(cacheListenerHandler), param(0), param(1))
            }

            // Galleries: a preview big enough for the thumbnail strip, the preview itself as
            // thumbnail, and the image shown downloaded with a progress bar.
            galleryPreview.before {
                capture("index").assign(RedditGallery.previewIndex(capture("previews")))
            }
            galleryThumbnail.before {
                val thumbnail = RedditGallery.thumbnail(thisObject)
                whenNotNull(thumbnail) { returnValue(thumbnail) }
            }
            galleryGlide.before {
                val url =
                    RedditGallery.loadImage(
                        thisObject,
                        param(0),
                        thisObject.field(viewerImage),
                        thisObject.field(viewerFlipper),
                        thisObject.field(viewerDoubleTap),
                    )
                whenNotNull(url) {
                    // A long press saves the image, as after the app's own load.
                    thisObject.call(saveOnLongPress, url)
                    returnVoid()
                }
            }

            // When the RedGIFs API answers with an error, the app shows RedGIFs' web page instead.
            // The error is remembered, so a deleted or missing video shows a message instead.
            redgifsError.before { PostMedia.onRedgifsError(capture("response")) }
            redgifsCancel.before {
                val message = PostMedia.redgifsErrorMessage()
                whenNotNull(message) {
                    thisObject.field(cancelListener).call(listenerCancel, message)
                    returnVoid()
                }
            }

            // Right after the app sets its default, the setting of its own browser. The app's list
            // of sites after it is skipped.
            javascriptDefault.after {
                PostMedia.applyJavascript(capture("settings"), param(0))
                returnVoid()
            }
        }
    }

private val handler = klass(POST_MEDIA_HANDLER)
private val start = handler.method("start") { params() }
private val loadGifContent = handler.method("loadGifContent") { params(Type.String) }
private val showPage = handler.method("showPage") { params(Type.Int) }
private val loadToCache = handler.method("loadToCache") { params(Type.String) }
private val handlerLink = handler.field("link")
private val handlerFlipper = handler.field("viewFlipper")
private val handlerVideo = handler.field("videoHandler")
private val handlerResolvedUrl = handler.field("resolvedUrl")

// The OkHttp callback of loadGifContent's Glide listener, used when Glide could not load the
// image. Its lambdas run when the response is not successful (4), no bitmap could be made (2), or
// showing it threw (3).
private val gifFallback = klass("$POST_MEDIA_HANDLER\$4\$1")
private val gifFallbackFailures =
    listOf(gifFallback.method("onFailure")) +
        listOf(2, 3, 4).map {
            gifFallback.method(
                "lambda\$onResponse\$$it\$com-onelouder-baconreader-PostMediaHandler\$4\$1"
            )
        }
private val gifFallbackListener = gifFallback.field("this\$1")
private val glideListenerHandler = klass("$POST_MEDIA_HANDLER\$4").field("this\$0")

// The download listener of the app's cache loader.
private val cacheListener = klass("$POST_MEDIA_HANDLER\$5")
private val receivedData = cacheListener.method("onReceivedData")
private val cacheListenerHandler = cacheListener.field("this\$0")

// Picks the preview of each gallery image: RedditMediaData.p, then List.get(0).
private val galleryPreview =
    klass(GALLERY_VIEWER)
        .method("getGalleryImages")
        .point("galleryPreview") {
            invokeInterface {
                owner("java.util.List")
                name("get")
            }
        }
        .captureArgumentAs("previews", 0)
        .captureArgumentAs("index", 1)

private val galleryThumbnail =
    klass("com.onelouder.baconreader.reddit_gallery.RedditImage").method("thumbnail")

// Loads the gallery image shown.
private val galleryGlide = klass(GALLERY_VIEWER).method("glide")
private val saveOnLongPress = klass(GALLERY_VIEWER).method("handleCurrentItemSaving")
private val viewerImage = klass(GALLERY_VIEWER).field("imageView")
private val viewerFlipper = klass(GALLERY_VIEWER).field("viewFlipper")
private val viewerDoubleTap = klass(GALLERY_VIEWER).field("gifTapListener")

// Handles the answer of the RedGIFs API, and cancels with Response.message() when it is an error.
private val redgifsError =
    klass(
            "com.onelouder.baconreader.media.gfycat.RedgifHandler\$resolveAsync\$1\$1\$1\$onResponse\$1"
        )
        .method("invokeSuspend")
        .point("redgifsError") {
            invokeVirtual {
                owner("retrofit2.Response")
                name("message")
            }
        }
        .captureArgumentAs("response", 0)

private val redgifsCancel = klass("$IMAGE_HELPER\$4").method("onCancel")
private val cancelListener = klass("$IMAGE_HELPER\$4").field("val\$listener")
private val listenerCancel = klass("$IMAGE_HELPER\$ImageHelperListener").method("onCancel")

// Sets the web view's JavaScript default, then turns it on for a list of sites.
private val javascriptDefault =
    klass("com.onelouder.baconreader.BrowserBase")
        .method("enableJavascript")
        .point("javascriptDefault") {
            invokeVirtual {
                owner("android.webkit.WebSettings")
                name("setJavaScriptEnabled")
            }
        }
        .captureArgumentAs("settings", 0)
