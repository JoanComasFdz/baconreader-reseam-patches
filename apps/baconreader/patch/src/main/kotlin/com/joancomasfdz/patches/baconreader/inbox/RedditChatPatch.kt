// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.inbox

import app.reseam.patch.after
import app.reseam.patch.appEntry
import app.reseam.patch.before
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.point
import app.reseam.patch.redirectTo
import app.reseam.patch.settings.section
import com.joancomasfdz.baconreader.chat.bindings.RedditChat
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.joancomasfdz.baconreader.settings.ChatSettings
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM
import com.joancomasfdz.patches.baconreader.core.SettingsPages
import com.joancomasfdz.patches.baconreader.core.baconReaderSettings
import com.joancomasfdz.patches.baconreader.core.mainActivity
import com.joancomasfdz.patches.baconreader.core.mainActivityOpenLinkset
import com.joancomasfdz.patches.baconreader.core.mainActivityPrepareMenu
import com.joancomasfdz.patches.baconreader.core.mainActivityResume

private const val DRAWER_HANDLER = "com.onelouder.baconreader.DrawerHandler"
private const val LINK_PROCESSOR = "com.onelouder.baconreader.LinkProcessor"
private const val LISTING = "com.onelouder.baconreader.reddit.Listing"

// BaconReader predates Reddit's chat, and Reddit has no public API for it. Reddit's mobile website
// has the chat, but it needs a login to the website: the app's OAuth login doesn't carry over. The
// chat shows over the main screen's post list, below its toolbar, so the drawer stays.
val redditChat =
    patch("Reddit chat") {
        description(
            "Adds Chat to the menu, next to the Inbox, which shows Reddit's chat from its mobile website on the main screen. The chat messages Reddit also sends to the inbox are counted next to Chat and notified as chat messages instead."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        settings(
            baconReaderSettings,
            section(
                SettingsPages.Chat,
                BaconReaderStrings.sectionRedditChat,
                ChatSettings.checkChatMessages,
            ),
        )

        execute {
            // The drawer's own icons come as grey PNGs for the light theme and white ones for the
            // dark themes, in 24 dp.
            val iconGrey =
                resources.addFile(
                    "drawable",
                    "baconreader_patches_chat_grey",
                    "res/drawable/baconreader_patches_chat_grey.xml",
                    chatIcon("#FF757575").toByteArray(),
                )
            val iconWhite =
                resources.addFile(
                    "drawable",
                    "baconreader_patches_chat_white",
                    "res/drawable/baconreader_patches_chat_white.xml",
                    chatIcon("#FFFFFFFF").toByteArray(),
                )
            // The icon and color of BaconReader's own notification of new messages.
            val notificationIcon =
                resources.id("drawable", "notify_new") ?: error("The notification icon is missing")
            val notificationColor =
                resources.id("color", "dkPrimary") ?: error("The notification color is missing")
            // MainActivity's views the chat goes between, and its toolbar's refresh.
            val viewIds =
                listOf(
                        "listLayout",
                        "ad_container",
                        "toolbar",
                        "toolbarTitle",
                        "toolbarFlipper",
                        "appBar",
                        "action_refresh",
                    )
                    .map { (resources.id("id", it) ?: error("id/$it is missing")).toInt() }
            appEntry {
                RedditChat.init(
                    application,
                    int(iconGrey.toInt()),
                    int(iconWhite.toInt()),
                    int(notificationIcon.toInt()),
                    int(notificationColor.toInt()),
                )
                RedditChat.initViews(
                    int(viewIds[0]),
                    int(viewIds[1]),
                    int(viewIds[2]),
                    int(viewIds[3]),
                    int(viewIds[4]),
                    int(viewIds[5]),
                    int(viewIds[6]),
                )
            }

            drawerItems.before { RedditChat.addChatItem(thisObject, capture("items")) }
            drawerItemClick.before {
                whenTrue(
                    RedditChat.onDrawerItemClick(thisObject.field(drawerOuter), param(0), param(2))
                ) {
                    returnVoid()
                }
            }
            mainCreate.before { RedditChat.onMainCreate(thisObject) }
            mainActivityOpenLinkset.after { RedditChat.onFeedOpened(thisObject) }
            mainActivityResume.before { RedditChat.onMainResume(thisObject) }
            mainPause.before { RedditChat.onMainPause(thisObject) }
            mainDestroy.before { RedditChat.onMainDestroy(thisObject) }
            mainBack.before {
                whenTrue(RedditChat.onMainBack(thisObject, thisObject.field(mainDrawer))) {
                    returnVoid()
                }
            }
            mainActivityPrepareMenu.after { RedditChat.prepareMenu(thisObject, param(0)) }
            mainMenuItem.before {
                whenTrue(RedditChat.onMenuItem(thisObject, param(0))) { returnTrue() }
            }
            mainActivityResult.before {
                whenTrue(
                    RedditChat.onMainActivityResult(thisObject, param(0), param(1), param(2))
                ) {
                    returnVoid()
                }
            }
            unreadMessages.redirectTo(RedditChat.unreadMessages)
            inboxMessages.redirectTo(RedditChat.inboxMessages)
            linkIntent.before {
                val intent =
                    RedditChat.chatIntent(
                        thisObject.field(linkContext),
                        thisObject.field(linkUrl),
                        thisObject.field(linkForceBrowser),
                    )
                whenNotNull(intent) { returnValue(intent) }
            }
        }
    }

/** A speech bubble with two lines of text, as an outline like the drawer's other icons. */
private fun chatIcon(color: String) =
    """
    <vector xmlns:android="http://schemas.android.com/apk/res/android"
        android:width="24dp"
        android:height="24dp"
        android:viewportWidth="24"
        android:viewportHeight="24">
        <path
            android:fillColor="#00000000"
            android:strokeColor="$color"
            android:strokeWidth="2"
            android:strokeLineCap="round"
            android:strokeLineJoin="round"
            android:pathData="M3,6A2,2 0,0 1,5,4H19A2,2 0,0 1,21,6V15A2,2 0,0 1,19,17H8L3,21Z M7,9H17 M7,13H14" />
    </vector>
    """
        .trimIndent()

private val drawerHandler = klass(DRAWER_HANDLER)

// The drawer's items, just before they show: updateDrawer builds the list, then hands it to the
// adapter.
private val drawerItems =
    drawerHandler
        .method("updateDrawer")
        .point("setItems") {
            invokeVirtual {
                owner("com.onelouder.baconreader.adapters.DrawerAdapter")
                name("setItems")
            }
        }
        .captureArgumentAs("items", 1)

// The drawer's item click listener, onItemClick(parent, view, position, id), which switches on the
// item's tag.
private val drawerItemClick = klass("$DRAWER_HANDLER\$1").method("onItemClick")

private val drawerOuter = klass("$DRAWER_HANDLER\$1").field("this\$0")

// The intent of a link BaconReader opens, from the comments, posts and LinkDispatcher.
private val linkIntent = klass(LINK_PROCESSOR).method("getIntent")

private val linkContext = klass(LINK_PROCESSOR).field("context")
private val linkUrl = klass(LINK_PROCESSOR).field("url")
private val linkForceBrowser = klass(LINK_PROCESSOR).field("forceBrowser")

// The unread messages of the app's background message check, which notifies of them and sets the
// inbox envelope.
private val unreadMessages =
    klass("com.onelouder.baconreader.services.workmanager.CheckMessagesExecutor")
        .method("onMessagesReceived")
        .point {
            invokeVirtual {
                owner(LISTING)
                name("getChildren")
            }
        }

// A page of messages the inbox loads, on any of its tabs.
private val inboxMessages =
    klass("com.onelouder.baconreader.adapters.InboxAdapter\$1")
        .method("onComplete") { params(LISTING) }
        .point {
            invokeVirtual {
                owner(LISTING)
                name("getChildren")
            }
        }

private val mainCreate = mainActivity.method("onCreate")
private val mainPause = mainActivity.method("onPause")
private val mainDestroy = mainActivity.method("onDestroy")
private val mainBack = mainActivity.method("onBackPressed")
private val mainMenuItem = mainActivity.method("onOptionsItemSelected")

// Delivers the result of the chat's file chooser, which the activity started.
private val mainActivityResult = mainActivity.method("onActivityResult")

private val mainDrawer = mainActivity.field("drawerHandler")
