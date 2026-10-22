// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.posts

import app.reseam.patch.before
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import com.joancomasfdz.baconreader.sidebar.bindings.DisableUnavailableSidebar
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings

// BaconReader offers "View Sidebar" on every feed, but only subreddits have a sidebar. On the
// others it only shows "This subreddit does not have a sidebar".
val disableUnavailableSidebar =
    patch("Disable unavailable sidebar") {
        description(
            "Disables \"View Sidebar\" on Front Page, All, Popular, Friends and multireddits, which have no sidebar."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        dependsOn(baconReaderSettings)

        execute {
            val sidebarId =
                resources.id("id", "action_sidebar")?.toInt() ?: error("id/action_sidebar missing")

            prepareMenu.before {
                DisableUnavailableSidebar.updateSidebarItem(
                    param(0),
                    thisObject.field(linksetKey),
                    int(sidebarId),
                )
            }
            openLinkset.before { DisableUnavailableSidebar.refreshMenu(thisObject) }
            firstLink.before { DisableUnavailableSidebar.refreshMenu(thisObject) }
        }
    }

private val mainActivity = klass("com.onelouder.baconreader.ui.main.MainActivity")

private val linksetKey = mainActivity.field("linksetKey")

private val prepareMenu = mainActivity.method("onPrepareOptionsMenu")

// Opens another feed.
private val openLinkset = mainActivity.method("openLinkset")

// Called when the posts of the feed have loaded.
private val firstLink = mainActivity.method("onFirstLink")
