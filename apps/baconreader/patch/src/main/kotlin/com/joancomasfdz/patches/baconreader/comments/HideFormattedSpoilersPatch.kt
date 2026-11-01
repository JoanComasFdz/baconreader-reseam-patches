// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.comments

import app.reseam.patch.after
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.point
import app.reseam.patch.replace
import com.joancomasfdz.baconreader.comments.bindings.HiddenSpoilers
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings

private const val ELEMENT = "com.onelouder.baconreader.parser.RedditParser\$Element"

// BaconReader hides a spoiler (>!...!<, <span class="md-spoiler-text"> in Reddit's HTML) behind a
// box revealed by tapping, but only when its content is a single plain text. A spoiler with
// formatting, a link or code inside was shown in plain sight. Any spoiler with content is hidden
// now, and the box covers all of it. Accessibility reads a hidden spoiler as "spoiler", not its
// text, which the box only paints over.
val hideFormattedSpoilers =
    patch("Hide formatted spoilers") {
        description("Hides spoilers that contain formatting, links or code, like other spoilers.")
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        dependsOn(baconReaderSettings)

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

            commentTextSet.captureArgumentAs("textView", 0).after {
                HiddenSpoilers.describe(capture("textView"))
            }
            postTextSet.captureArgumentAs("textView", 0).after {
                HiddenSpoilers.describe(capture("textView"))
            }
            spoilerTapped.after { HiddenSpoilers.describe(param(0)) }
        }
    }

// Whether an element of a comment's or post's HTML is a spoiler the app hides.
private val isSpoiler =
    klass("com.onelouder.baconreader.parser.RedditSpanner").method("isInternalSpoiler")

private val elementChildren = klass(ELEMENT).field("children")

private const val COMMENT_HOLDER = "com.onelouder.baconreader.adapters.CommentHolder"

// The text of a comment, set in CommentHolder.updateView.
private val commentTextSet =
    klass(COMMENT_HOLDER).method("updateView").point("commentTextSet") {
        invokeVirtual {
            owner("android.widget.TextView")
            name("setText")
            params("java.lang.CharSequence", "android.widget.TextView\$BufferType")
        }
    }

// The text of a post, set in DetailLinkFragment.updateSelftext.
private val postTextSet =
    klass("com.onelouder.baconreader.adapters.DetailLinkFragment").method("updateSelftext").point(
        "postTextSet"
    ) {
        invokeVirtual {
            owner("android.widget.TextView")
            name("setText")
            params("java.lang.CharSequence")
        }
    }

// A tap on a spoiler shows or hides it, and sets the view's text again.
private val spoilerTapped =
    klass("com.onelouder.baconreader.parser.SpoilerClickableSpan").method("onClick")
