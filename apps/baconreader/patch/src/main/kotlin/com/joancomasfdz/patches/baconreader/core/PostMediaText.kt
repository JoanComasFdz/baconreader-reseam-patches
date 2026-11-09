// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.core

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.point

// Where PostMediaHandler's constructor sets the text of an image, GIF or video post, under its
// media: an ExpandableTextView, captured as "textView". The constructor's other setText call shows
// load errors.
val postMediaText =
    klass("com.onelouder.baconreader.PostMediaHandler")
        .method("<init>")
        .point("postMediaText") {
            invokeVirtual {
                owner("com.onelouder.baconreader.ExpandableTextView")
                name("setText")
                params(Type.CharSequence)
            }
        }
        .captureArgumentAs("textView", 0)
