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
import com.joancomasfdz.baconreader.selecttext.bindings.SelectText
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings

private const val CONTROL_BAR = "com.onelouder.baconreader.controlbar.ControlBar"
private const val POST_CONTROL_BAR = "com.onelouder.baconreader.controlbar.PostControlBar"
private const val CONTROLBAR_BASE = "com.onelouder.baconreader.controlbar.ControlbarBase"
private const val POPUP_MENU = "androidx.appcompat.widget.PopupMenu"

// BaconReader can't select a part of a post or comment: their text views take a tap and a long
// press for the list (showing the action bar), and "Copy" copies the whole text as markdown. A
// dialog shows the text as the app draws it, where it can be selected.
val selectText =
    patch(BaconReaderStrings.patchSelectText) {
        description(BaconReaderStrings.patchSelectTextDescription)
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        dependsOn(baconReaderSettings)

        execute {
            // The item of the post menu, and the dialog's text, made in code: the extension finds
            // them by name, and the e2e tests the text.
            resources.addId("baconreader_patches_select_text")
            resources.addId("baconreader_patches_selected_text")

            copyButtonSet.after {
                SelectText.addButton(
                    thisObject,
                    thisObject.field(controlBarActivity),
                    thisObject.field(controlBarThing),
                )
            }
            postMenuInflated.after {
                SelectText.addMenuItem(
                    thisObject
                        .field(postMenu)
                        .callVirtual(POPUP_MENU, "getMenu", "()Landroid/view/Menu;"),
                    thisObject.field(postBarActivity),
                )
            }
            postAction.before {
                whenTrue(
                    SelectText.onPostAction(
                        param(0),
                        thisObject.field(postBarActivity),
                        thisObject.field(postBarLink),
                    )
                ) {
                    returnTrue()
                }
            }
        }
    }

private val controlBarActivity = klass(CONTROL_BAR).field("activity")

private val controlBarThing = klass(POST_CONTROL_BAR).field("post")

// The action bar of a comment, or of a post in the list, adds its buttons for the post or comment
// it shows; the ones past the bar's width go to its "More options" menu. "Select text" goes right
// after "Copy": this.copyButton = setButton("Copy").setImage(...).
private val copyButtonSet =
    klass(POST_CONTROL_BAR).method("updateButtons").point("copyButtonSet") {
        opcode(Opcode.IPUT_OBJECT)
        field {
            owner(POST_CONTROL_BAR)
            name("copyButton")
        }
    }

private val postMenu = klass(CONTROLBAR_BASE).field("morePopup")

private val postBarActivity = klass(CONTROLBAR_BASE).field("activity")

private val postBarLink = klass(CONTROLBAR_BASE).field("link")

// The bar under a post, and under a post card, inflates its ⋮ menu (link_card.xml) and sets its
// click listener.
private val postMenuInflated =
    klass(CONTROLBAR_BASE).method("drawBar").point("postMenuInflated") {
        invokeVirtual {
            owner(POPUP_MENU)
            name("setOnMenuItemClickListener")
        }
    }

// Runs the tapped item of a post's ⋮ menu, by its id.
private val postAction = klass(CONTROLBAR_BASE).method("onAction")
