// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.core

import app.reseam.patch.dex.AccessFlags
import app.reseam.patch.patch

// ThreadManager.Thread, a post's loaded comments, is package-private. Android refuses access to it
// from extension code outside BaconReader's package, so the patches that read it make it public.
val threadAccess = patch {
    compatibleWith(BACONREADER, BACONREADER_PREMIUM)

    execute {
        val thread =
            bytecode.findClass("com.onelouder.baconreader.data.ThreadManager\$Thread")
                ?: error("The comment thread class is missing")
        thread.setAccessFlags(thread.info.accessFlags.toInt() or AccessFlags.PUBLIC)
    }
}
