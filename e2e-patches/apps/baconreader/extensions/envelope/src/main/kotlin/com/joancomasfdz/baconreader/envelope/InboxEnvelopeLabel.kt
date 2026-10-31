// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("InboxEnvelopeLabel")

package com.joancomasfdz.baconreader.envelope

import androidx.appcompat.widget.Toolbar
import app.reseam.runtime.Export

// Test only. With unread messages, the main screen's left-menu button shows an orange envelope
// instead of the menu icon; both are only "Navigate up" to UiAutomator. The button's description
// names the envelope while it shows, so a test can see it.

// BaconReader's orange envelope.
private const val ENVELOPE = "ic_nd_mail_orange"

private const val ENVELOPE_LABEL = ", orange envelope"

/** Replaces DrawerHandler.updateDrawer's toolbar.setNavigationIcon(resId). */
@Export
fun setNavigationIcon(toolbar: Toolbar?, resId: Int) {
    if (toolbar == null) return
    toolbar.setNavigationIcon(resId)
    val description = toolbar.navigationContentDescription?.toString()?.removeSuffix(ENVELOPE_LABEL)
    val envelope = resId != 0 && toolbar.resources.getResourceEntryName(resId) == ENVELOPE
    toolbar.setNavigationContentDescription(
        if (envelope) (description ?: "") + ENVELOPE_LABEL else description
    )
}
