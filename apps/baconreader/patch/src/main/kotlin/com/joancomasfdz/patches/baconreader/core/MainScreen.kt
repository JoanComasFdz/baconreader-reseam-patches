// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.core

import app.reseam.patch.klass
import app.reseam.patch.method

/** The main screen, with the post list and the drawer. */
val mainActivity = klass("com.onelouder.baconreader.ui.main.MainActivity")

/** The main screen coming back to the front, MainActivity.onResume. */
val mainActivityResume = mainActivity.method("onResume")

/**
 * Opens the feed of MainActivity.linksetKey: on start, and when the subreddit, sort or query
 * changes.
 */
val mainActivityOpenLinkset = mainActivity.method("openLinkset")

/** Shows or hides the toolbar's menu items. */
val mainActivityPrepareMenu = mainActivity.method("onPrepareOptionsMenu")
