// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.account

import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.point
import app.reseam.patch.skipWhen
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM

// "Log out" in the menu's accounts logs out of every account at once and keeps them listed, each to
// be logged in to again. It's rarely needed, and sits right above the accounts. Each account's ✕
// still removes that account.
val removeLogOut =
    patch(BaconReaderStrings.patchRemoveLogOut) {
        description(BaconReaderStrings.patchRemoveLogOutDescription)
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)

        execute { logOutItem.skipWhen { bool(true) } }
    }

// Builds the menu's items, then hands them to the adapter. With an account logged in and the
// accounts open, the first of them is "Log out", the only item whose taps log out.
private val updateDrawer = klass("com.onelouder.baconreader.DrawerHandler").method("updateDrawer")

// items.add(new DrawerItem(2, "Log out", icon, 100)), whose result isn't used.
private val logOutItem =
    updateDrawer.point("logOutItem") { string("Log out") }.next { invokeInterface { name("add") } }
