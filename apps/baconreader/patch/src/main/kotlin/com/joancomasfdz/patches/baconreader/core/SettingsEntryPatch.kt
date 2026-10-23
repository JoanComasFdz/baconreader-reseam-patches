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
            "Adds a Patches entry at the end of BaconReader's settings, which opens the patches' settings."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        dependsOn(baconReaderSettings)

        execute {
            menuSections.after {
                capture("sections").assign(SettingsEntry.withEntry(capture("sections")))
            }
            openSection.before {
                whenTrue(SettingsEntry.isEntry(staticField(sectionTitles), param(0))) {
                    openSettings()
                    returnVoid()
                }
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
