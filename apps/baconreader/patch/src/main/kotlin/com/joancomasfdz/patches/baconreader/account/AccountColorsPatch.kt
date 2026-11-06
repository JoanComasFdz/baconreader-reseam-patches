// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.account

import app.reseam.patch.after
import app.reseam.patch.appEntry
import app.reseam.patch.before
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.point
import app.reseam.patch.settings.section
import com.joancomasfdz.baconreader.accountcolors.bindings.AccountColors
import com.joancomasfdz.baconreader.settings.AccountColorsSettings
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings

private const val DRAWER_HANDLER = "com.onelouder.baconreader.DrawerHandler"
private const val DRAWER_ADAPTER = "com.onelouder.baconreader.adapters.DrawerAdapter"

// BaconReader switches between several Reddit accounts from the menu, but every account looks the
// same once it is in use: only the menu's header names it.
val accountColors =
    patch("Account colors") {
        description(
            "Lets you pick a color for each account in the menu, and shows the color of the account in use as a line under the toolbars."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        settings(
            baconReaderSettings,
            section(BaconReaderStrings.sectionAccountColors, AccountColorsSettings.accountColors),
        )

        execute {
            // The toolbar of every screen that has one, and the ✕ of the menu's account rows.
            val toolbarId = resources.id("id", "toolbar") ?: error("id/toolbar is missing")
            val iconId = resources.id("id", "icon") ?: error("id/icon is missing")
            appEntry {
                AccountColors.init(application, int(toolbarId.toInt()), int(iconId.toInt()))
            }
            drawerRow.after { AccountColors.bindRow(thisObject, param(0), result) }
            drawerItems.before { AccountColors.onDrawerUpdated() }
            accountRemoved.before { AccountColors.onAccountRemoved(capture("name")) }
        }
    }

// A row of the menu, made or reused: getView(position, convertView, parent).
private val drawerRow = klass(DRAWER_ADAPTER).method("getView")

// The drawer's items, just before they show: updateDrawer builds them, then hands them to the
// adapter. It runs when the drawer is made, and again whenever the account changes.
private val drawerItems =
    klass(DRAWER_HANDLER).method("updateDrawer").point("setItems") {
        invokeVirtual {
            owner(DRAWER_ADAPTER)
            name("setItems")
        }
    }

// The ✕ of an account row asks first; its OK removes the account with removeSession(context, name).
private val accountRemoved =
    klass("$DRAWER_HANDLER\$2\$1")
        .method("onClick")
        .point("removeSession") {
            invokeStatic {
                owner("com.onelouder.baconreader.data.SessionManager")
                name("removeSession")
            }
        }
        .captureArgumentAs("name", 1)
