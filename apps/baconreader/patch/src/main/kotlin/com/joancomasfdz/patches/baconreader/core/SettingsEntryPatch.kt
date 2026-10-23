// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.core

import app.reseam.patch.after
import app.reseam.patch.before
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.point
import app.reseam.patch.settings.openSettings
import com.joancomasfdz.baconreader.settingsentry.bindings.SettingsEntry

private const val SETTINGS_FRAGMENT = "com.onelouder.baconreader.SettingsFragment"

// BaconReader's settings menu lists the titles of its sections, and opens a section by its
// position in that list. The entry goes last, so the app's own positions stay the same.
val settingsEntry =
    patch("Patches entry in settings") {
        description(
            "Adds a Patches entry at the end of BaconReader's settings, which opens the patches' settings, and shows the patches' version at the bottom of the menu."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        dependsOn(baconReaderSettings)

        execute {
            val textColor =
                resources.id("attr", "textColor") ?: error("The drawer's text color is missing")
            menuSections.after {
                capture("sections").assign(SettingsEntry.withEntry(capture("sections")))
            }
            openSection.before {
                whenTrue(SettingsEntry.isEntry(staticField(sectionTitles), param(0))) {
                    openSettings()
                    returnVoid()
                }
            }
            drawerItems.before {
                SettingsEntry.addVersionFooter(thisObject.field(drawerList), int(textColor.toInt()))
            }
        }
    }

private val sectionTitles = klass(SETTINGS_FRAGMENT).field("sectionTitles")

private val menuSections =
    klass("com.onelouder.baconreader.SettingsMenuFragment")
        .method("initAdapter")
        .point("menuSections") {
            field {
                owner(SETTINGS_FRAGMENT)
                name("sectionTitles")
            }
        }
        .captureAs("sections")

private val openSection = klass("com.onelouder.baconreader.SettingsActivity").method("openFragment")

private const val DRAWER_HANDLER = "com.onelouder.baconreader.DrawerHandler"

private val drawerList = klass(DRAWER_HANDLER).field("listView")

// The drawer's items, just before they show: updateDrawer builds them, then hands them to the
// adapter. It runs when the drawer is made, and again on each change.
private val drawerItems =
    klass(DRAWER_HANDLER).method("updateDrawer").point("setItems") {
        invokeVirtual {
            owner("com.onelouder.baconreader.adapters.DrawerAdapter")
            name("setItems")
        }
    }
