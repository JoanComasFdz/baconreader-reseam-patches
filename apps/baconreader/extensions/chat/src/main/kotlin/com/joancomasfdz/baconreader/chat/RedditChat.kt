// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("RedditChat")

package com.joancomasfdz.baconreader.chat

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.AdapterView
import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.common.ErrorDialog
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.onelouder.baconreader.DrawerHandler
import com.onelouder.baconreader.adapters.DrawerAdapter
import java.util.Locale

// Reddit has no public chat API, and the app's OAuth login can't open Reddit's website. So the chat
// is Reddit's mobile website chat in a WebView, where users log in to the website once.

private const val TAG = "RedditChat"

internal const val CHAT_URL = "https://www.reddit.com/chat"

// DrawerAdapter's item layout, and the tags of the drawer's own items that the Chat item goes after
// or before.
private const val TYPE_ITEM = 3
private const val TAG_INBOX = 3
private const val TAG_SUBREDDIT = 7
private const val TAG_SETTINGS = 10

// Not one of DrawerHandler's ACTION_ tags, so its click handler ignores the item.
private const val TAG_CHAT = 0x43484154

private var app: Application? = null
private var chatIconGrey = 0
private var chatIconWhite = 0

/** Called when the app starts, with the icons the patch added to the app's resources. */
@Export
fun init(application: Application?, iconGrey: Int, iconWhite: Int) {
    app = application
    chatIconGrey = iconGrey
    chatIconWhite = iconWhite
}

/**
 * Called by DrawerHandler.updateDrawer with the drawer's items, before they show: adds Chat after
 * the Inbox and its folders, or before Subreddit when no account is logged in.
 */
@Export
fun addChatItem(handler: DrawerHandler?, items: MutableList<Any?>?) {
    if (items == null) return
    try {
        val drawerItems = items.filterIsInstance<DrawerAdapter.DrawerItem>()
        val item = DrawerAdapter.DrawerItem(TYPE_ITEM, chatLabel(), chatIcon(drawerItems), TAG_CHAT)
        val lastInbox = items.indexOfLast { (it as? DrawerAdapter.DrawerItem)?.tag == TAG_INBOX }
        val subreddit = items.indexOfFirst {
            (it as? DrawerAdapter.DrawerItem)?.tag == TAG_SUBREDDIT
        }
        when {
            lastInbox >= 0 -> items.add(lastInbox + 1, item)
            subreddit >= 0 -> items.add(subreddit, item)
            else -> items.add(item)
        }
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not add Chat to the menu", exception)
    }
}

/** Called first when a drawer item is tapped; true when it was Chat, which it opens. */
@Export
fun onDrawerItemClick(handler: DrawerHandler?, parent: AdapterView<*>?, position: Int): Boolean {
    if (parent == null) return false
    return try {
        val item = parent.getItemAtPosition(position) as? DrawerAdapter.DrawerItem
        if (item?.tag != TAG_CHAT) return false
        handler?.closeDrawer()
        open(parent.context, null)
        true
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not open the chat", exception)
        true
    }
}

/**
 * Called first in LinkProcessor.getIntent, which opens the links BaconReader handles: the chat
 * screen for Reddit chat links, or null for the app's own handling.
 */
@Export
fun chatIntent(context: Context?, url: String?, forceBrowser: Boolean): Intent? {
    if (context == null || url == null || forceBrowser) return null
    return try {
        val uri = Uri.parse(url)
        if (!isChatLink(uri)) return null
        intent(context, uri.buildUpon().scheme("https").build())
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not open the chat link $url", exception)
        null
    }
}

internal fun open(context: Context, uri: Uri?) {
    context.startActivity(intent(context, uri))
}

internal fun intent(context: Context, uri: Uri?): Intent =
    Intent(context, ChatActivity::class.java).apply { if (uri != null) data = uri }

/** Links of Reddit's chat: www.reddit.com/chat and its pages, and chat.reddit.com. */
internal fun isChatLink(uri: Uri): Boolean {
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    if (scheme != "https" && scheme != "http") return false
    val host = uri.host?.lowercase(Locale.ROOT) ?: return false
    if (host == "chat.reddit.com") return true
    if (host != "reddit.com" && !host.endsWith(".reddit.com")) return false
    val path = uri.path.orEmpty()
    return path == "/chat" || path.startsWith("/chat/")
}

private fun chatLabel(): String = BaconReaderStrings.chatEntry

/**
 * The icon in the color of the drawer's own icons: their dark themes use white icons, the light one
 * grey ones.
 */
private fun chatIcon(items: List<DrawerAdapter.DrawerItem>): Int {
    val settings = items.firstOrNull { it.tag == TAG_SETTINGS }?.icon ?: 0
    val resources = app?.resources
    if (settings == 0 || resources == null) return chatIconGrey
    return try {
        val name = resources.getResourceEntryName(settings)
        if (name.contains("white")) chatIconWhite else chatIconGrey
    } catch (exception: Exception) {
        chatIconGrey
    }
}
