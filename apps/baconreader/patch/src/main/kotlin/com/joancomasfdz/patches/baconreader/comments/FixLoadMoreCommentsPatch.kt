// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.comments

import app.reseam.patch.before
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import com.joancomasfdz.baconreader.comments.bindings.MoreChildrenFallback
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.threadAccess

// Since late October 2026, Reddit answers api/morechildren with a 403 "blocked by network
// security" page, logged in or not, whatever the client: "load more comments" and the comments
// loaded at the end of a long thread stay hidden. Reddit still serves a comment's thread, which
// holds the same comments. The app asks api/morechildren first, in case Reddit serves it again.
val fixLoadMoreComments =
    patch("Fix load more comments") {
        description("Makes \"load more comments\" load the hidden comments again.")
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        dependsOn(threadAccess)

        execute {
            loadMore.before { MoreChildrenFallback.loading(param(2), param(3)) }
            getMoreChildren.before {
                param(4).assign(MoreChildrenFallback.guard(param(0), param(1), param(2), param(4)))
            }
        }
    }

// Loads a "more" of a thread: loadMore(context, owner, threadKey, more, listener). It is
// synchronized and calls getMoreChildren right away.
private val loadMore = klass("com.onelouder.baconreader.data.ThreadManager").method("loadMore")

// Asks Reddit for the comments behind a "more": getMoreChildren(context, linkId, sort, children,
// listener).
private val getMoreChildren =
    klass("com.onelouder.baconreader.reddit.RedditApi").method("getMoreChildren")
