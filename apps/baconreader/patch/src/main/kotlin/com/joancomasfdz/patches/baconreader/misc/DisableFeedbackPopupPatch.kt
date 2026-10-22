// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.misc

import app.reseam.patch.alwaysReturn
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM

// Every screen reports its time to ReviewLegacyImpl, which asks for feedback about BaconReader once
// enough time was spent in the app. The time tracking and the popup are turned off.
val disableFeedbackPopup =
    patch("Disable feedback popup") {
        description(
            "Disables the \"Please take a few moments to provide feedback about BaconReader\" popup."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)

        execute {
            reviewResume.alwaysReturn()
            reviewPause.alwaysReturn()
            showReview.alwaysReturn()
            reviewAvailable.alwaysReturn(false)
        }
    }

private val review = klass("com.onelouder.baconreader.review.ReviewLegacyImpl")

// Called from BaconReaderActivity.onResume: shows the popup when enough time was spent in the app.
private val reviewResume = review.method("resume")

// Called from BaconReaderActivity.onPause: adds the session's time.
private val reviewPause = review.method("pause")

private val showReview = review.method("showReview")

private val reviewAvailable = review.method("isAvailable")
