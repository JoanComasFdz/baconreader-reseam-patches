// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.editor

import app.reseam.patch.after
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.point
import com.joancomasfdz.baconreader.editor.bindings.MarkdownEditor
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings

private const val POST_ACTIVITY = "com.onelouder.baconreader.PostActivity"
private const val COMPOSE_MESSAGE_ACTIVITY = "com.onelouder.baconreader.ComposeMessageActivity"
private const val SUBMIT_ACTIVITY = "com.onelouder.baconreader.SubmitActivity"

// BaconReader's text boxes for comments, replies, edits, messages and text posts are a few lines
// tall, and its format bar hides behind an "A" button in the toolbar. The boxes take the screen
// instead, with a bar of Reddit's formats and a character counter above them, and show the
// markdown formatted while typing.
val markdownEditor =
    patch("Markdown editor") {
        description(
            "Makes the text box of comments, replies, edits, messages and text posts as big as possible, with a bar of Reddit's formats and a character counter, and shows the formatting while typing."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        dependsOn(baconReaderSettings)

        execute {
            // PostActivity is the screen of comments, replies and edits.
            for (layoutSet in listOf(postLayoutSet, messageLayoutSet)) {
                layoutSet.after { MarkdownEditor.onEditorScreen(thisObject) }
            }
            textPostPage.after { MarkdownEditor.onEditorView(result) }

            for (menuInflated in
                listOf(postMenuInflated, messageMenuInflated, submitMenuInflated)) {
                menuInflated.captureArgumentAs("menu", 2).after {
                    MarkdownEditor.hideFormatButton(thisObject, capture("menu"))
                }
            }
        }
    }

private fun layoutSet(activity: String) =
    klass(activity).method("onCreate").point("${activity.substringAfterLast('.')}.layoutSet") {
        invokeVirtual { name("setContentView") }
    }

private fun menuInflated(activity: String) =
    klass(activity).method("onCreateOptionsMenu").point(
        "${activity.substringAfterLast('.')}.menuInflated"
    ) {
        invokeVirtual {
            owner("android.view.MenuInflater")
            name("inflate")
        }
    }

private val postLayoutSet = layoutSet(POST_ACTIVITY)

private val messageLayoutSet = layoutSet(COMPOSE_MESSAGE_ACTIVITY)

private val textPostPage = klass("com.onelouder.baconreader.SubmitTextPage").method("onCreateView")

private val postMenuInflated = menuInflated(POST_ACTIVITY)

private val messageMenuInflated = menuInflated(COMPOSE_MESSAGE_ACTIVITY)

private val submitMenuInflated = menuInflated(SUBMIT_ACTIVITY)
