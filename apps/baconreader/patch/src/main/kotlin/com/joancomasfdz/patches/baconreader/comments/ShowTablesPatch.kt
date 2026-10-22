// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.comments

import app.reseam.patch.after
import app.reseam.patch.before
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.point
import com.joancomasfdz.baconreader.tables.bindings.ShowTables
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings

private const val SPANNER_BASE = "com.onelouder.baconreader.parser.RedditSpannerBase"
private const val COMMENT_HOLDER = "com.onelouder.baconreader.adapters.CommentHolder"

// BaconReader flattens Reddit's tables into "header: value" lines, which loses the rows and
// columns. The table is drawn as a grid in the text instead, sized to the text's width once it is
// set. Comment bodies have the app's own touch listener, which is wrapped so the grid's button,
// sideways scrolling and links get their touches too.
val showTables =
    patch("Show tables") {
        description(
            "Shows tables in comments and posts as a grid, with wide tables that expand and scroll sideways."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        dependsOn(baconReaderSettings)

        execute {
            appendTable.before {
                // The paragraph break before the table comes first: making the cells' links with
                // the app's spanner would take it otherwise. Flushing it again is a no-op when the
                // app flattens the table after all.
                thisObject.call(appendPendingParagraph, param(0))
                val start = ShowTables.appendTable(thisObject, param(0), param(1))
                whenNotEqual(start, int(-1)) { returnValue(start) }
            }

            commentTextSet.captureArgumentAs("textView", 0).after {
                ShowTables.onTextSet(capture("textView"))
            }
            postTextSet.captureArgumentAs("textView", 0).after {
                ShowTables.onTextSet(capture("textView"))
            }

            commentBodyTouch.captureArgumentAs("view", 0).captureArgumentAs("listener", 1).after {
                ShowTables.installTouch(capture("view"), capture("listener"))
            }
        }
    }

private val spannerBase = klass(SPANNER_BASE)

private val appendTable = spannerBase.method("appendTable")

private val appendPendingParagraph = spannerBase.method("appendPendingParagraph")

private val commentTextSet =
    klass(COMMENT_HOLDER).method("updateView").point("commentTextSet") {
        invokeVirtual {
            owner("android.widget.TextView")
            name("setText")
            params("java.lang.CharSequence", "android.widget.TextView\$BufferType")
        }
    }

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

// The comment body's listener; the constructor's other touch listener is the whole row's.
private val commentBodyTouch =
    klass(COMMENT_HOLDER).method("<init>").point("commentBodyTouch") {
        invokeVirtual {
            owner("android.widget.TextView")
            name("setOnTouchListener")
        }
    }
