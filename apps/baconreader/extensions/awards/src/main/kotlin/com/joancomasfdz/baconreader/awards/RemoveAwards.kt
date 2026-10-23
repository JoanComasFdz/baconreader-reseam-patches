// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("RemoveAwards")

package com.joancomasfdz.baconreader.awards

import android.view.Menu
import app.reseam.runtime.Export

// Reddit's API no longer reports awards (all_awardings is always empty since 2023), and the award
// endpoints of the old coins system are gone.

private const val ACTION_GIVE_AWARD = "Give Award"
private const val ACTION_VIEW_AWARDS = "View Award Details"

/** Whether a button of the comment and post action bars is about awards. */
@Export
fun isAwardAction(text: String?): Boolean = text == ACTION_GIVE_AWARD || text == ACTION_VIEW_AWARDS

/** Hides an item of the ⋮ menu of posts. */
@Export
fun hideMenuItem(menu: Menu?, id: Int) {
    menu?.findItem(id)?.isVisible = false
}
