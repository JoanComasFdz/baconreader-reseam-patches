// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("DisableUnavailableSidebar")

package com.joancomasfdz.baconreader.sidebar

import android.app.Activity
import android.view.Menu
import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.common.ErrorDialog
import com.onelouder.baconreader.data.LinksetKey
import com.onelouder.baconreader.data.LinksetManager

// "View Sidebar" only works for a subreddit. Front Page, All, Popular, Friends, multireddits, and
// Random before it has loaded, only show "This subreddit does not have a sidebar".

private const val TAG = "DisableUnavailableSidebar"

/**
 * The app only prepares its menu again on rotation. Called when another feed opens and when its
 * posts load, which resolves Random to its subreddit.
 */
@Export
fun refreshMenu(activity: Activity?) {
    activity?.runOnUiThread { activity.invalidateOptionsMenu() }
}

/** Called when the overflow menu of the post list is prepared. */
@Export
fun updateSidebarItem(menu: Menu?, linksetKey: LinksetKey?, sidebarId: Int) {
    try {
        menu?.findItem(sidebarId)?.isEnabled = hasSidebar(linksetKey)
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not update the sidebar menu item", exception)
    }
}

private fun hasSidebar(linksetKey: LinksetKey?): Boolean {
    if (linksetKey == null) return false
    // Random resolves to its subreddit once the posts load, as the app's openSidebar() does.
    val redditId = LinksetManager.getResolvedRedditId(linksetKey) ?: return false
    if (!redditId.isPureSubreddit) return false
    // The app counts Popular as a subreddit, but it has no sidebar.
    return !"popular".equals(redditId.name, ignoreCase = true)
}
