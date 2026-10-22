// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.comments

import app.reseam.patch.after
import app.reseam.patch.before
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.settings.section
import com.joancomasfdz.baconreader.comments.bindings.LoadMoreComments
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.joancomasfdz.baconreader.settings.LoadMoreCommentsSettings
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.SettingsPages
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings
import com.joancomasfdz.patches.baconreader.core.threadAccess

// BaconReader requests a post's comments without a limit, so Reddit returns its default of about
// 200 and hides the rest behind "load more comments", often for one or two replies.
val loadMoreComments =
    patch("Load more comments") {
        description(
            "Adds options to load up to 500 comments when opening a post instead of about 200, and to load the \"load more comments\" with few replies automatically."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        dependsOn(threadAccess)
        settings(
            baconReaderSettings,
            section(
                SettingsPages.Comments,
                BaconReaderStrings.sectionLoadMoreComments,
                LoadMoreCommentsSettings.commentLimit,
                LoadMoreCommentsSettings.autoLoadMoreComments,
            ),
        )

        execute {
            // The app sends a limit of 0, which gets Reddit's default: the setting instead.
            getComments.before { param(5).assign(LoadMoreComments.commentLimit(param(5))) }
            processData.after { LoadMoreComments.onItemsProcessed(thisObject) }
        }
    }

// Requests the comments of a post: getComments(context, linkId, commentId, context, sort, limit,
// listener).
private val getComments = klass("com.onelouder.baconreader.reddit.RedditApi").method("getComments")

// Gives the comment list its items, from ThreadManager's data. The small "load more comments" are
// loaded after it.
private val processData =
    klass("com.onelouder.baconreader.adapters.CommentsAdapter").method("processData")
