// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.posts

import app.reseam.patch.Type
import app.reseam.patch.alwaysReturn
import app.reseam.patch.before
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.point
import app.reseam.patch.skipWhen
import com.joancomasfdz.baconreader.awards.bindings.RemoveAwards
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM

private const val CONTROL_BAR = "com.onelouder.baconreader.controlbar.ControlBar"
private const val CONTROL_BAR_BUTTON = "com.onelouder.baconreader.controlbar.ControlBarButton"
private const val POPUP_MENU = "androidx.appcompat.widget.PopupMenu"
private const val PREFERENCES = "com.onelouder.baconreader.Preferences"
private const val SETTING_IDS = "com.onelouder.baconreader.adapters.SettingIds"

// Reddit's API no longer reports awards, so BaconReader's award icons never show anything, and
// giving awards used Reddit's coins, which are discontinued.
val removeAwards =
    patch("Remove awards") {
        description(
            "Removes the award icons and the \"Give Award\" and \"View Award Details\" actions, which no longer work."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)

        execute {
            val giveAward =
                resources.id("id", "action_give_gold")?.toInt()
                    ?: error("id/action_give_gold missing")
            val viewAwards =
                resources.id("id", "action_view_awards")?.toInt()
                    ?: error("id/action_view_awards missing")

            // The comment and post action bars. The callers set up the button they get back, so
            // the award buttons are made in a layout outside the bar.
            setButton.before {
                whenTrue(RemoveAwards.isAwardAction(param(0))) {
                    val holder =
                        newInstance(
                            "android.widget.LinearLayout",
                            "(Landroid/content/Context;)V",
                            thisObject.field(controlBarActivity),
                        )
                    val button =
                        newInstance(CONTROL_BAR_BUTTON, "(Landroid/view/ViewGroup;)V", holder)
                    button.callVirtual(
                        CONTROL_BAR_BUTTON,
                        "setText",
                        "(Ljava/lang/String;)Lcom/onelouder/baconreader/controlbar/ControlBarButton;",
                        param(0),
                    )
                    returnValue(button)
                }
            }

            postMenuShow.before {
                val menu =
                    capture("popup").callVirtual(POPUP_MENU, "getMenu", "()Landroid/view/Menu;")
                RemoveAwards.hideMenuItem(menu, int(giveAward))
                RemoveAwards.hideMenuItem(menu, int(viewAwards))
            }

            // The award icons of posts and comments, in lists and on the post screen.
            hideAwardIcons.alwaysReturn(true)
            hideAwardIconsOnDetailView.alwaysReturn(true)

            // The two "Hide Reddit Awards" settings no longer change anything.
            awardIconsSetting.skipWhen { bool(true) }
            awardIconsDetailViewSetting.skipWhen { bool(true) }
        }
    }

private val controlBarActivity = klass(CONTROL_BAR).field("activity")

// Adds a button to the action bar of comments and posts.
private val setButton = klass(CONTROL_BAR).method("setButton") { params(Type.String) }

// The ⋮ menu of posts, shown by the post bar's click handler.
private val postMenuShow =
    klass("com.onelouder.baconreader.controlbar.ControlbarBase")
        .method("onClick")
        .point("postMenuShow") {
            invokeVirtual {
                owner(POPUP_MENU)
                name("show")
            }
        }
        .captureArgumentAs("popup", 0)

private val hideAwardIcons =
    klass(PREFERENCES).method("hideAwardIcons") {
        params()
        returns(Type.Boolean)
    }

private val hideAwardIconsOnDetailView =
    klass(PREFERENCES).method("hideAwardIconsOnDetailView") {
        params()
        returns(Type.Boolean)
    }

private val initSettings =
    klass("com.onelouder.baconreader.adapters.SettingsAdapter").method("initSettings")

private val awardIconsSetting = addSetting("HIDE_AWARD_ICONS")

private val awardIconsDetailViewSetting = addSetting("HIDE_AWARD_ICONS_DETAIL_VIEW")

// list.add(new Setting(SettingIds.<id>, ...)), whose result isn't used.
private fun addSetting(id: String) =
    initSettings
        .point("${id}Setting") {
            field {
                owner(SETTING_IDS)
                name(id)
            }
        }
        .next { invokeVirtual { name("add") } }
