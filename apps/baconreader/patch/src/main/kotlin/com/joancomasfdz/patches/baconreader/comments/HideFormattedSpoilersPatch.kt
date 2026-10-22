// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.comments

import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.replace
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM

private const val ELEMENT = "com.onelouder.baconreader.parser.RedditParser\$Element"

// BaconReader hides a spoiler (>!...!<, <span class="md-spoiler-text"> in Reddit's HTML) behind a
// box revealed by tapping, but only when its content is a single plain text. A spoiler with
// formatting, a link or code inside was shown in plain sight. Any spoiler with content is hidden
// now, and the box covers all of it.
val hideFormattedSpoilers =
    patch("Hide formatted spoilers") {
        description("Hides spoilers that contain formatting, links or code, like other spoilers.")
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)

        execute {
            isSpoiler.replace {
                val spanClass =
                    param(0)
                        .callVirtual(
                            ELEMENT,
                            "getAttribute",
                            "(Ljava/lang/String;)Ljava/lang/String;",
                            string("class"),
                        )
                val spoiler =
                    string("md-spoiler-text")
                        .callVirtual(
                            "java.lang.String",
                            "equals",
                            "(Ljava/lang/Object;)Z",
                            spanClass,
                        )
                whenTrue(spoiler) {
                    val children = param(0).field(elementChildren)
                    whenNotNull(children) {
                        val empty = children.callInterface("java.util.List", "isEmpty", "()Z")
                        whenFalse(empty) { returnTrue() }
                    }
                }
                returnFalse()
            }
        }
    }

// Whether an element of a comment's or post's HTML is a spoiler the app hides.
private val isSpoiler =
    klass("com.onelouder.baconreader.parser.RedditSpanner").method("isInternalSpoiler")

private val elementChildren = klass(ELEMENT).field("children")
