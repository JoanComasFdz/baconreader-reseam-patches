// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.core

import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.point

// Where CommentHolder's constructor gives the comment body its touch listener; the constructor's
// other touch listener is the whole row's.
val commentBodyTouch =
    klass("com.onelouder.baconreader.adapters.CommentHolder").method("<init>").point(
        "commentBodyTouch"
    ) {
        invokeVirtual {
            owner("android.widget.TextView")
            name("setOnTouchListener")
        }
    }
