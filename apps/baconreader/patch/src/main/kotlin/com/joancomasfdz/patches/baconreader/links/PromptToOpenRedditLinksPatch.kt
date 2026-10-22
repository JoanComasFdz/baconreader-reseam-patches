// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.links

import app.reseam.patch.before
import app.reseam.patch.patch
import com.joancomasfdz.baconreader.openlinks.bindings.OpenLinks
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings
import com.joancomasfdz.patches.baconreader.core.mainActivityResume

// Since Android 12, an app can't turn on its own links: Android only opens a site's links in the
// apps the site verifies, and reddit.com only verifies Reddit's official app. Users must select
// the links themselves on the app's "Open by default" screen, which most never find.
val promptToOpenRedditLinks =
    patch("Prompt to open Reddit links") {
        description(
            "Asks once to set up Reddit links to open in BaconReader, which Android doesn't let the app do by itself."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        dependsOn(baconReaderSettings)

        execute { mainActivityResume.before { OpenLinks.onMainResume(thisObject) } }
    }
