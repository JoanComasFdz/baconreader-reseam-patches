// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.posts

import app.reseam.patch.after
import app.reseam.patch.before
import app.reseam.patch.dex.Opcode
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.point
import com.joancomasfdz.baconreader.textposticon.bindings.TextPostIcon
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings

// Text posts in the post list have a button that opens the post's text under the title.
// BaconReader draws it as a triangle in a circle, which looks like a video that did not load, and
// rotates it when the text opens. It becomes a page icon with a chevron that flips instead.
val textPostIcon =
    patch("Text post icon") {
        description(
            "Shows the button that opens a text post's text in the post list as a page with a chevron, instead of a play button."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        dependsOn(baconReaderSettings)

        execute {
            for (holder in listOf(linkHolder, linkCardHolder)) {
                holder.buttonSet.after { TextPostIcon.install(thisObject.field(holder.button)) }
                holder.rotateArrow.before {
                    val handled =
                        TextPostIcon.setExpanded(
                            thisObject.field(holder.button),
                            param(0),
                            param(1),
                        )
                    whenTrue(handled) {
                        thisObject.set(holder.rotated, param(0))
                        returnVoid()
                    }
                }
            }
        }
    }

/** A row of the post list: LinkHolder in the list view, LinkCardHolder in the card view. */
private class PostRow(className: String) {
    private val row = klass(className)

    /** The text post button, "next". */
    val button = row.field("next")

    /** Whether the app's icon is turned to "open", which the app reads when it rotates it next. */
    val rotated = row.field("rotated")

    /** this.next = (ImageView) findViewById(R.id.next), in the constructor. */
    val buttonSet =
        row.method("<init>").point("${className.substringAfterLast('.')}.buttonSet") {
            opcode(Opcode.IPUT_OBJECT)
            field {
                owner(className)
                name("next")
            }
        }

    /** rotateSelftextArrow(expanded, duration). */
    val rotateArrow = row.method("rotateSelftextArrow")
}

private val linkHolder = PostRow("com.onelouder.baconreader.adapters.LinkHolder")

private val linkCardHolder = PostRow("com.onelouder.baconreader.adapters.LinkCardHolder")
