// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.e2e

import app.reseam.patch.CompatiblePackage
import app.reseam.patch.invoke
import app.reseam.patch.patch
import app.reseam.patch.types.EncodedVal

// The main bundle's core/BaconReader.kt: this bundle is built apart from it.
internal val BACONREADER: CompatiblePackage = "com.onelouder.baconreader"("6.1.4")
internal val BACONREADER_PREMIUM: CompatiblePackage = "com.onelouder.baconreader.premium"("6.1.4")

private const val DEFAULT_BASE_URL = "http://127.0.0.1:5080"

// Reddit detects automation, rate-limits logins and changes its content, so the e2e tests talk to
// a fake Reddit server in the test process instead, which the phone reaches through
// `adb reverse`. Only full URL prefixes move: BaconReader recognizes Reddit links in posts by bare
// domains (reddit.com, v.redd.it), and those stay.
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

        // After every patch's execute, of every loaded bundle: the extensions they merged hold
        // Reddit URLs too, and "Spoof client" moves ssl.reddit.com to www.reddit.com first.
        afterDependents {
            val prefixes = redditPrefixes(options[baseUrl].trimEnd('/'))
            var strings = 0
            for ((old, new) in prefixes) {
                strings += bytecode.replaceStringsContaining(old) { it.replace(old, new) }
            }

            // Constants.REDDIT_DOMAIN and REDDIT_GALLERY_DOMAIN are read at run time, not inlined.
            var fields = 0
            for (dexClass in bytecode.classes) {
                for (field in dexClass.staticFields) {
                    val value = (field.initialValue as? EncodedVal.StringVal)?.field0 ?: continue
                    val moved = prefixes.fold(value) { url, (old, new) -> url.replace(old, new) }
                    if (moved != value) {
                        dexClass.setStaticFieldValue(field.name, EncodedVal.StringVal(moved))
                        fields++
                    }
                }
            }
            log.info("Pointed $strings strings and $fields fields at the fake Reddit server")
        }
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

private val MEDIA_HOSTS = listOf("i.redd.it", "preview.redd.it", "v.redd.it")

// Each full URL prefix and what it becomes; paths after it are kept.
private fun redditPrefixes(baseUrl: String): List<Pair<String, String>> =
    REDDIT_ORIGINS.map { it to baseUrl } +
        MEDIA_HOSTS.map { "https://$it/" to "$baseUrl/_media/$it/" } +
        ("https://cf-baconreader.onelouder.com/" to "$baseUrl/_onelouder/")
