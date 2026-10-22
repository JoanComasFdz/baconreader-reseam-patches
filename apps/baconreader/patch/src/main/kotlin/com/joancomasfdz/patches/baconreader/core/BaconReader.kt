// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.core

import app.reseam.patch.CompatiblePackage
import app.reseam.patch.invoke

const val BACONREADER_PACKAGE = "com.onelouder.baconreader"
const val BACONREADER_PREMIUM_PACKAGE = "com.onelouder.baconreader.premium"

// Pinned: 6.1.4 is BaconReader's last release, the one the targets were read against. The patches
// rewrite constants and method bodies, which goes wrong silently on other code.
val BACONREADER: CompatiblePackage = BACONREADER_PACKAGE("6.1.4")
val BACONREADER_PREMIUM: CompatiblePackage = BACONREADER_PREMIUM_PACKAGE("6.1.4")
