// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.e2e

import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.points
import app.reseam.patch.redirectTo
import com.joancomasfdz.baconreader.envelope.bindings.InboxEnvelopeLabel

// With unread messages, the main screen's left-menu button shows an orange envelope instead of the
// menu icon, an image UiAutomator can't tell apart: both buttons are "Navigate up". The e2e tests
// of "Fix unread messages" check the envelope by the button's description, which this names.
val e2eInboxEnvelopeLabel =
    patch("E2E inbox envelope label") {
        description(
            "Test only: adds \"orange envelope\" to the left-menu button's description while it shows the orange envelope."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)

        execute { navigationIcons.forEach { redirectTo(InboxEnvelopeLabel.setNavigationIcon) } }
    }

// Both of updateDrawer's toolbar.setNavigationIcon(id): logged out, the menu icon; logged in, the
// envelope while MessageManager has new messages, else the menu icon.
private val navigationIcons =
    klass("com.onelouder.baconreader.DrawerHandler").method("updateDrawer").points(
        "navigationIcons"
    ) {
        invokeVirtual {
            owner("androidx.appcompat.widget.Toolbar")
            name("setNavigationIcon")
        }
    }
