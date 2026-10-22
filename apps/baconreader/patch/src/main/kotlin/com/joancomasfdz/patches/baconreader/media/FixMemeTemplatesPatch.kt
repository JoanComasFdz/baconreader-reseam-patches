// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.media

import app.reseam.patch.after
import app.reseam.patch.before
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import com.joancomasfdz.baconreader.memes.bindings.FixMemeTemplates
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings

private const val IMAGES = "com.handmark.drawchat.bitmapfun.provider.Images"
private const val IMAGE_GRID_FRAGMENT = "com.handmark.drawchat.bitmapfun.ui.ImageGridFragment"

// The meme templates of the drawing screen were on BaconReader's server, which answers 404 for
// all of them, so none of them load.
val fixMemeTemplates =
    patch("Fix meme templates") {
        description(
            "Makes the meme templates of the drawing screen load again, with Imgflip's most used templates."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        dependsOn(baconReaderSettings)

        execute {
            imagesInit.after { setStatic(imageUrls, FixMemeTemplates.templateUrls()) }

            // The app saves the grid's thumbnail as the background. The full template is
            // downloaded instead.
            templateClick.before {
                val activity =
                    thisObject.callVirtual(
                        IMAGE_GRID_FRAGMENT,
                        "getActivity",
                        "()Landroidx/fragment/app/FragmentActivity;",
                    )
                val item =
                    param(0)
                        .callVirtual(
                            "android.widget.AdapterView",
                            "getItemAtPosition",
                            "(I)Ljava/lang/Object;",
                            param(2),
                        )
                whenTrue(FixMemeTemplates.onTemplateClick(activity, item)) { returnVoid() }
            }
        }
    }

// The URLs of the template grid.
private val imagesInit = klass(IMAGES).method("<clinit>")

private val imageUrls = klass(IMAGES).field("imageUrls")

private val templateClick = klass(IMAGE_GRID_FRAGMENT).method("onItemClick")
