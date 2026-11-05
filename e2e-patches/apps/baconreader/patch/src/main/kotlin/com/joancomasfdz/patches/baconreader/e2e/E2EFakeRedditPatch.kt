// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.e2e

import app.reseam.patch.CompatiblePackage
import app.reseam.patch.Type
import app.reseam.patch.after
import app.reseam.patch.before
import app.reseam.patch.invoke
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.types.EncodedVal
import com.joancomasfdz.baconreader.fakereddit.bindings.E2EFakeReddit

// The main bundle's core/BaconReader.kt: this bundle is built apart from it.
internal val BACONREADER: CompatiblePackage = "com.onelouder.baconreader"("6.1.4")
internal val BACONREADER_PREMIUM: CompatiblePackage = "com.onelouder.baconreader.premium"("6.1.4")

private const val DEFAULT_BASE_URL = "http://127.0.0.1:5080"

// Reddit detects automation, rate-limits logins and changes its content, so the e2e tests talk to
// a fake Reddit server in the test process instead, which the phone reaches through
// `adb reverse`. It answers the third-party services the patches fetch from too (Imgur, RedGIFs,
// YouTube, X, Bluesky, Reddit's emotes, Imgflip). Only full URL prefixes move: BaconReader
// recognizes Reddit and Imgur links in posts by bare domains (reddit.com, v.redd.it, imgur.com),
// and those stay.
val e2eFakeReddit =
    patch("E2E fake Reddit") {
        description("Test only: points BaconReader at a fake Reddit server instead of Reddit.")
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)

        val baseUrl =
            stringOption(
                "baseUrl",
                title = "Base URL",
                description = "The fake Reddit server's URL, such as $DEFAULT_BASE_URL.",
                default = DEFAULT_BASE_URL,
                required = true,
            )

        // The fake's media come moved like the app's own URLs, over plain HTTP, and two checks
        // assume
        // Reddit's: a post is a Reddit video only by the host v.redd.it, and a video is saved from
        // an
        // https URL.
        execute {
            redditVideoUrl.before {
                whenTrue(E2EFakeReddit.isMovedRedditVideo(param(0))) { returnTrue() }
            }
            savedVideoUrl.after { returnValue(E2EFakeReddit.keepFakeScheme(param(0), result)) }
        }

        // After every patch's execute, of every loaded bundle: the extensions they merged hold
        // Reddit URLs too, and "Spoof client" moves ssl.reddit.com to www.reddit.com first.
        afterDependents {
            val prefixes = redditPrefixes(options[baseUrl].trimEnd('/'))
            var strings = 0
            for ((old, new) in prefixes) {
                strings +=
                    bytecode.replaceStringsContaining(old) {
                        if (it.startsWith(REDDIT_GALLERY)) it else it.replace(old, new)
                    }
            }

            // Constants.REDDIT_DOMAIN and REDDIT_GALLERY_DOMAIN are read at run time, not inlined.
            var fields = 0
            for (dexClass in bytecode.classes) {
                for (field in dexClass.staticFields) {
                    val value = (field.initialValue as? EncodedVal.StringVal)?.field0 ?: continue
                    if (value.startsWith(REDDIT_GALLERY)) continue
                    val moved = prefixes.fold(value) { url, (old, new) -> url.replace(old, new) }
                    if (moved != value) {
                        dexClass.setStaticFieldValue(field.name, EncodedVal.StringVal(moved))
                        fields++
                    }
                }
            }
            // "Fix Redgifs API" authenticates only requests to RedGIFs' API host, and answers its
            // old OAuth path itself: moved, both checks name the fake's host and path.
            val fakeHost = java.net.URI(options[baseUrl]).host
            strings +=
                bytecode.replaceStringsContaining(REDGIFS_API_HOST) {
                    if (it == REDGIFS_API_HOST) fakeHost else it
                }
            strings +=
                bytecode.replaceStringsContaining(REDGIFS_OAUTH_PATH) {
                    if (it == REDGIFS_OAUTH_PATH) REDGIFS_PATH + REDGIFS_OAUTH_PATH else it
                }
            log.info("Pointed $strings strings and $fields fields at the fake Reddit server")
        }
    }

// Whether a URL is a v.redd.it video: its host is v.redd.it.
private val redditVideoUrl =
    klass("com.onelouder.baconreader.reddit.RedditVideo").method("isValid") { params(Type.String) }

// The URL a video is saved from: the video's URL, made https.
private val savedVideoUrl =
    klass("com.onelouder.baconreader.imageutils.SaveImage").method("getVideoFilename") {
        params(Type.String)
    }

private val REDDIT_ORIGINS =
    listOf(
        "https://www.reddit.com",
        "https://oauth.reddit.com",
        "https://api.reddit.com",
        "https://ssl.reddit.com",
        "https://reddit.com",
        "https://m.reddit.com",
        "http://www.reddit.com",
        "http://reddit.com",
    )

// The app knows a gallery by Reddit's own address, https://www.reddit.com/gallery/<id>, which the
// fake's gallery posts link to as Reddit's do. Moved, "Fix Reddit links" would not take them for
// Reddit links, as it does on Reddit.
private const val REDDIT_GALLERY = "https://www.reddit.com/gallery"

private val MEDIA_HOSTS =
    listOf(
        "i.redd.it",
        "preview.redd.it",
        "v.redd.it",
        "i.imgur.com",
        "i.ytimg.com",
        "pbs.twimg.com",
        // The Snoomoji emotes, whose GIF "Embed comment images" finds from an emote's name.
        "www.redditstatic.com",
        // The meme templates "Fix meme templates" shows.
        "i.imgflip.com",
    )

// Imgur's API, which "Fix Imgur API" calls instead of BaconReader's RapidAPI proxy.
private const val IMGUR_API = "https://api.imgur.com/"

// RedGIFs' API, which BaconReader calls with the temporary tokens "Fix Redgifs API" fetches.
private const val REDGIFS_API = "https://api.redgifs.com/"
private const val REDGIFS_API_HOST = "api.redgifs.com"
private const val REDGIFS_OAUTH_PATH = "/v2/oauth/client"
private const val REDGIFS_PATH = "/_redgifs"

// YouTube's oEmbed endpoint, where "Embed comment images" asks for a video's title. Only this
// address moves: comments and posts link YouTube at its own, which the patches recognize.
private const val YOUTUBE_OEMBED = "https://www.youtube.com/oembed"

// X's embed service and Bluesky's public API, where "Embed comment images" asks for a post. X's
// photos move with the media hosts: the patch asks for a small one only at X's own address.
private const val X_SYNDICATION = "https://cdn.syndication.twimg.com/"
private const val BLUESKY_API = "https://public.api.bsky.app/"

// Each full URL prefix and what it becomes; paths after it are kept.
private fun redditPrefixes(baseUrl: String): List<Pair<String, String>> =
    REDDIT_ORIGINS.map { it to baseUrl } +
        MEDIA_HOSTS.map { "https://$it/" to "$baseUrl/_media/$it/" } +
        ("https://cf-baconreader.onelouder.com/" to "$baseUrl/_onelouder/") +
        (IMGUR_API to "$baseUrl/_imgur/") +
        (REDGIFS_API to "$baseUrl$REDGIFS_PATH/") +
        (YOUTUBE_OEMBED to "$baseUrl/_youtube/oembed") +
        (X_SYNDICATION to "$baseUrl/_x/") +
        (BLUESKY_API to "$baseUrl/_bluesky/")
