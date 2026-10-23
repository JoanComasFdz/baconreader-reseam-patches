// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("RedditChat")

package com.joancomasfdz.baconreader.chat

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.widget.AdapterView
import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.common.ErrorDialog
import com.joancomasfdz.baconreader.common.InboxChats
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.onelouder.baconreader.DrawerHandler
import com.onelouder.baconreader.adapters.DrawerAdapter
import com.onelouder.baconreader.reddit.Listing
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.WeakHashMap

// Reddit has no public chat API, and the app's OAuth login can't open Reddit's website. So the chat
// is Reddit's mobile website chat in a WebView over MainActivity's post list, where users log in to
// the website once.

private const val TAG = "RedditChat"

internal const val CHAT_URL = "https://www.reddit.com/chat"

private const val MAIN_ACTIVITY = "com.onelouder.baconreader.ui.main.MainActivity"

// Opens MainActivity with the chat at this URL.
private const val EXTRA_CHAT_URL = "com.joancomasfdz.baconreader.chat.URL"

// DrawerAdapter's item layout, and the tags of the drawer's own items that the Chat item goes after
// or before.
private const val TYPE_ITEM = 3
private const val TAG_INBOX = 3
private const val TAG_SUBREDDIT = 7
private const val TAG_SETTINGS = 10

// Not one of DrawerHandler's ACTION_ tags, so its click handler ignores the item.
private const val TAG_CHAT = 0x43484154

private val main = Handler(Looper.getMainLooper())

private var app: Application? = null
private var chatIconGrey = 0
private var chatIconWhite = 0

/** The main screen's drawer, to show a new unread count. Only used on the main thread. */
private var drawer = WeakReference<DrawerHandler>(null)

private var views: MainViews? = null

/** The chat of each MainActivity. Only used on the main thread. */
private val screens = WeakHashMap<Activity, MainScreen>()

private class MainScreen {
    var panel: ChatPanel? = null

    /** The chat to show once the activity has opened its first feed. */
    var pendingUrl: String? = null

    /** Opened for a chat link or the notification: back leaves the activity. */
    var openedForChat = false
}

/**
 * Called when the app starts, with the icons the patch added to the app's resources and the app's
 * own notification icon and color.
 */
@Export
fun init(
    application: Application?,
    iconGrey: Int,
    iconWhite: Int,
    notificationIcon: Int,
    notificationColor: Int,
) {
    app = application
    InboxChats.patched = true
    application?.let { ChatMessages.init(it) }
    chatIconGrey = iconGrey
    chatIconWhite = iconWhite
    ChatMessages.notificationIcon = notificationIcon
    ChatMessages.notificationColor = notificationColor
}

/** Called when the app starts, with the ids of MainActivity's views and its refresh menu item. */
@Export
fun initViews(
    listLayout: Int,
    adContainer: Int,
    toolbar: Int,
    toolbarTitle: Int,
    toolbarFlipper: Int,
    appBar: Int,
    refreshItem: Int,
) {
    views =
        MainViews(
            listLayout,
            adContainer,
            toolbar,
            toolbarTitle,
            toolbarFlipper,
            appBar,
            refreshItem,
        )
}

/** Called at the end of MainActivity.onCreate: an intent for the chat shows it. */
@Export
fun onMainCreate(activity: Activity?) {
    if (activity == null) return
    try {
        val url = activity.intent?.getStringExtra(EXTRA_CHAT_URL) ?: return
        val screen = screens.getOrPut(activity) { MainScreen() }
        screen.pendingUrl = url
        screen.openedForChat = true
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not open the chat", exception)
    }
}

/**
 * Called at the end of MainActivity.openLinkset, which opens a feed: on start, and when a feed is
 * chosen in the drawer or the toolbar. The chat waiting for the first feed shows; any other feed
 * leaves the chat.
 */
@Export
fun onFeedOpened(activity: Activity?) {
    if (activity == null) return
    try {
        val screen = screens[activity] ?: return
        val url = screen.pendingUrl
        if (url != null) {
            screen.pendingUrl = null
            show(activity, url)
        } else {
            screen.panel?.hide(restoreTitle = false)
            ChatMessages.onChatHidden()
        }
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not leave the chat", exception)
    }
}

/** Called at the start of MainActivity.onBackPressed; true when the chat handled it. */
@Export
fun onMainBack(activity: Activity?, drawerHandler: DrawerHandler?): Boolean {
    if (activity == null) return false
    return try {
        val screen = screens[activity] ?: return false
        val panel = screen.panel ?: return false
        if (!panel.showing) return false
        // An open drawer closes first, as on the post list.
        if (drawerHandler?.handleReturn() == true) return true
        val handled = panel.back(finishInstead = screen.openedForChat && !activity.isTaskRoot)
        if (!panel.showing) ChatMessages.onChatHidden()
        handled
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not go back in the chat", exception)
        false
    }
}

/** Called at the end of MainActivity.onPrepareOptionsMenu. */
@Export
fun prepareMenu(activity: Activity?, menu: Menu?) {
    if (activity == null || menu == null) return
    try {
        screens[activity]?.panel?.prepareMenu(menu)
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not change the menu for the chat", exception)
    }
}

/** Called first in MainActivity.onOptionsItemSelected: refresh reloads the chat while it shows. */
@Export
fun onMenuItem(activity: Activity?, item: MenuItem?): Boolean {
    if (activity == null || item == null) return false
    val panel = screens[activity]?.panel ?: return false
    if (!panel.showing || item.itemId != views?.refreshItem) return false
    return try {
        panel.reload()
        true
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not reload the chat", exception)
        true
    }
}

/** Called first in MainActivity.onActivityResult; true for the chat's file chooser. */
@Export
fun onMainActivityResult(
    activity: Activity?,
    requestCode: Int,
    resultCode: Int,
    data: Intent?,
): Boolean {
    if (activity == null || requestCode != ChatPanel.REQUEST_FILES) return false
    return try {
        screens[activity]?.panel?.onActivityResult(resultCode, data)
        true
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not attach the chosen files", exception)
        true
    }
}

/**
 * Called at the start of MainActivity.onResume: checks for chat messages in the background, and
 * resumes the chat when it shows.
 */
@Export
fun onMainResume(activity: Activity?) {
    try {
        val panel = activity?.let { screens[it]?.panel }
        if (panel != null && panel.showing) {
            panel.onResume()
            ChatMessages.onChatShown(activity)
        }
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not resume the chat", exception)
    }
    followAccount()
}

/** Called at the start of MainActivity.onPause. */
@Export
fun onMainPause(activity: Activity?) {
    if (activity == null) return
    try {
        val panel = screens[activity]?.panel ?: return
        panel.onPause()
        if (panel.showing) ChatMessages.onChatHidden()
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not pause the chat", exception)
    }
}

/** Called at the start of MainActivity.onDestroy: the WebView goes with its activity. */
@Export
fun onMainDestroy(activity: Activity?) {
    if (activity == null) return
    try {
        val screen = screens.remove(activity) ?: return
        if (screen.panel?.showing == true) ChatMessages.onChatHidden()
        screen.panel?.destroy()
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not close the chat", exception)
    }
}

private fun show(activity: Activity, url: String?) {
    followAccount()
    val views = views ?: error("The chat's view ids are missing")
    val screen = screens.getOrPut(activity) { MainScreen() }
    val panel = screen.panel ?: ChatPanel(activity, views).also { screen.panel = it }
    panel.show(url)
    ChatMessages.onChatShown(activity)
}

/** On the main thread: gives the chat the login of the account shown. */
private fun followAccount() {
    val context = app ?: return
    try {
        ChatAccounts.follow(context) {
            for (screen in screens.values) screen.panel?.restart()
        }
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not switch the chat to the account shown", exception)
    }
}

/**
 * Called in place of Listing.getChildren in the app's background message check, which asks Reddit
 * for the unread messages: the chat messages are counted for Chat, and the rest returned.
 */
@Export
fun unreadMessages(listing: Listing?, type: Class<*>?): List<*>? {
    @Suppress("UNCHECKED_CAST")
    val messages = listing?.getChildren(type as Class<Any?>?) ?: return null
    val context = app ?: return messages
    return try {
        ChatMessages.onUnread(context, messages).also { refreshDrawer() }
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not count the chat messages", exception)
        messages
    }
}

/**
 * Called in place of Listing.getChildren when the inbox loads a page of messages: the chat messages
 * are left out.
 */
@Export
fun inboxMessages(listing: Listing?, type: Class<*>?): List<*>? {
    @Suppress("UNCHECKED_CAST")
    val messages = listing?.getChildren(type as Class<Any?>?) ?: return null
    return try {
        if (!InboxChats.separate) messages
        else messages.filterNot { ChatMessages.isChat(it) }.toMutableList()
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not leave the chat messages out of the inbox", exception)
        messages
    }
}

private fun refreshDrawer() {
    main.post {
        try {
            drawer.get()?.updateDrawer()
        } catch (exception: Exception) {
            ErrorDialog.report(TAG, "Could not show the unread chat messages", exception)
        }
    }
}

/**
 * Called by DrawerHandler.updateDrawer with the drawer's items, before they show: adds Chat after
 * the Inbox and its folders, or before Subreddit when no account is logged in.
 */
@Export
fun addChatItem(handler: DrawerHandler?, items: MutableList<Any?>?) {
    if (items == null) return
    try {
        if (handler != null) drawer = WeakReference(handler)
        // The drawer updates when the account changes.
        followAccount()
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
        val activity = parent.context as? Activity ?: return true
        show(activity, null)
        true
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not open the chat", exception)
        true
    }
}

/**
 * Called first in LinkProcessor.getIntent, which opens the links BaconReader handles: the main
 * screen with the chat for Reddit chat links, or null for the app's own handling.
 */
@Export
fun chatIntent(context: Context?, url: String?, forceBrowser: Boolean): Intent? {
    if (context == null || url == null || forceBrowser) return null
    return try {
        val uri = Uri.parse(url)
        if (!isChatLink(uri)) return null
        intent(context, uri.buildUpon().scheme("https").build().toString())
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not open the chat link $url", exception)
        null
    }
}

/** A new main screen that shows the chat, at [url] or its start. */
internal fun intent(context: Context, url: String?): Intent =
    Intent().setClassName(context, MAIN_ACTIVITY).putExtra(EXTRA_CHAT_URL, url ?: CHAT_URL)

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

private fun chatLabel(): String {
    val unread = app?.let { ChatMessages.unread(it) } ?: 0
    return if (unread > 0) BaconReaderStrings.chatEntryUnread(unread.toString())
    else BaconReaderStrings.chatEntry
}

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
