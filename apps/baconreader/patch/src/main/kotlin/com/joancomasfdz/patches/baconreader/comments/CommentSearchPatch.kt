// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.comments

import app.reseam.patch.after
import app.reseam.patch.dex.Opcode
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.point
import com.joancomasfdz.baconreader.search.bindings.CommentSearch
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings
import com.joancomasfdz.patches.baconreader.core.threadAccess

private const val DETAIL_LINK_FRAGMENT = "com.onelouder.baconreader.adapters.DetailLinkFragment"
private const val COMMENT_HOLDER = "com.onelouder.baconreader.adapters.CommentHolder"

// Reddit's API has no comment search, unlike its website, and BaconReader has none either. The
// comments of a post are already loaded, so the search runs on them.
val commentSearch =
    patch("Search comments") {
        description(
            "Adds a search button to the sort bar of posts, to find, highlight and jump between the comments with a text or user name, or show only those comments."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        dependsOn(baconReaderSettings, threadAccess)

        execute {
            collapseButtonSet.after {
                CommentSearch.addSearchButton(thisObject, thisObject.field(collapseButton))
            }
            processData.after { CommentSearch.onItemsProcessed(thisObject) }
            commentBodySet.after {
                CommentSearch.highlight(thisObject.field(holderComment), capture("body"))
            }
        }
    }

private val collapseButton = klass(DETAIL_LINK_FRAGMENT).field("collapseButton")

// The post's header sets up its "COLLAPSE ALL" button, next to which the search button goes.
private val collapseButtonSet =
    klass(DETAIL_LINK_FRAGMENT).method("resetHeader").point("collapseButtonSet") {
        opcode(Opcode.IPUT_OBJECT)
        field {
            owner(DETAIL_LINK_FRAGMENT)
            name("collapseButton")
        }
    }

// Gives the comment list its items, from ThreadManager's data. "Show only matching comments"
// replaces them.
private val processData =
    klass("com.onelouder.baconreader.adapters.CommentsAdapter").method("processData")

private val holderComment = klass(COMMENT_HOLDER).field("comment")

// Sets the text of a comment: this.commentBody.setText(text, bufferType). The matches are
// highlighted after it.
private val commentBodySet =
    klass(COMMENT_HOLDER)
        .method("updateView")
        .point("commentBodySet") {
            field { name("commentBody") }
            then(within = 2) { invokeVirtual { name("setText") } }
        }
        .captureArgumentAs("body", 0)
