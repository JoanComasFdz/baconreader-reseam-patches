// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("CommentSearch")

package com.joancomasfdz.baconreader.search

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.common.ErrorDialog
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.onelouder.baconreader.adapters.CommentsAdapter
import com.onelouder.baconreader.connectivity.Tasks
import com.onelouder.baconreader.data.ThreadItem
import com.onelouder.baconreader.data.ThreadKey
import com.onelouder.baconreader.data.ThreadManager
import com.onelouder.baconreader.reddit.Comment
import com.onelouder.baconreader.reddit.More
import com.onelouder.baconreader.reddit.ThingData
import java.lang.reflect.Field

/**
 * Searches the comments of a post, like the "Search comments" box of Reddit's website, which the
 * API doesn't have. A search button in the sort bar ("Best ▾ ... COLLAPSE ALL") turns the bar into
 * a find bar, which stays at the top of the comments when the bar scrolls away. It searches the
 * loaded comments, also inside collapsed ones, highlights the matches and scrolls between them, or
 * shows only the matching comments. "Search all" first loads the comments behind "load more
 * comments".
 */
private object Searches {
    const val TAG = "CommentSearch"
    const val SEARCH_DELAY_MS = 300L

    /** The safety limit of "Search all": requests of 100 comments or more each. */
    const val MAX_LOAD_REQUESTS = 60

    val main = Handler(Looper.getMainLooper())

    /** The open search, whose matches are highlighted. Main thread only, like the rest. */
    var active: Search? = null

    val fields = HashMap<String, Field?>()
}

/** The find bar of a post and its matches. */
private class Search(var fragment: Any?, val activity: Activity) {
    lateinit var bar: LinearLayout
    lateinit var input: EditText
    lateinit var counter: TextView
    lateinit var searchAll: TextView
    lateinit var filterButton: ImageView
    var sortBar: View? = null
    var listView: ListView? = null
    var positioner: ViewTreeObserver.OnPreDrawListener? = null

    /** Shows only the matching comments. */
    var filter = false
    var linkName: String? = null
    var query = ""

    /** The matching comments, in thread order. */
    var matches: List<Comment> = emptyList()
    var current = -1
    var currentComment: Comment? = null
    var loadingAll = false
    val searchRunnable = Runnable { runSearch(this, true) }

    val hasBar
        get() = ::bar.isInitialized
}

/** Adds the search button next to "COLLAPSE ALL", once DetailLinkFragment has found it. */
@Export
fun addSearchButton(fragment: Any?, collapseButton: TextView?) {
    try {
        if (fragment == null || collapseButton == null) return
        val sortBar = collapseButton.parent as? RelativeLayout ?: return
        val activity = collapseButton.context as Activity
        for (i in 0 until sortBar.childCount) {
            val tag = sortBar.getChildAt(i).tag
            if (tag is Search) {
                tag.fragment = fragment
                return
            }
        }

        val search = Search(fragment, activity)
        val button = ImageView(activity)
        button.tag = search
        button.id = View.generateViewId()
        button.contentDescription = BaconReaderStrings.commentSearchHint
        button.setImageDrawable(
            tinted(activity, "abc_ic_search_api_material", collapseButton.textColors)
        )
        button.scaleType = ImageView.ScaleType.CENTER_INSIDE
        val padding = dp(activity, 12)
        button.setPadding(padding, 0, padding, 0)
        button.background =
            themeDrawable(activity, android.R.attr.selectableItemBackgroundBorderless)
        button.setOnClickListener { open(search, collapseButton) }

        if (collapseButton.id == View.NO_ID) collapseButton.id = View.generateViewId()
        val params =
            RelativeLayout.LayoutParams(dp(activity, 48), ViewGroup.LayoutParams.MATCH_PARENT)
        params.addRule(RelativeLayout.LEFT_OF, collapseButton.id)
        sortBar.addView(button, params)
    } catch (ex: Exception) {
        ErrorDialog.report(Searches.TAG, "Could not add the comment search button", ex)
    }
}

/** Highlights the matches of the search, after CommentHolder sets the text of a comment. */
@Export
fun highlight(comment: Comment?, body: TextView?) {
    val search = Searches.active ?: return
    if (search.query.isEmpty() || comment == null || body == null) return
    try {
        if (search.linkName != comment.link_id) return
        val text = body.text
        val spannable = text as? Spannable ?: SpannableString(text)
        val accent = accentColor(search.activity)
        val color = if (comment === search.currentComment) accent else withAlpha(accent, 0x55)
        val string = spannable.toString()
        val query = search.query
        var found = false
        var i = 0
        while (i + query.length <= string.length) {
            if (string.regionMatches(i, query, 0, query.length, ignoreCase = true)) {
                spannable.setSpan(
                    BackgroundColorSpan(color),
                    i,
                    i + query.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                i += query.length
                found = true
            } else {
                i++
            }
        }
        if (found && text !is Spannable) body.text = spannable
    } catch (ex: Exception) {
        Log.i(Searches.TAG, "Could not highlight the comment", ex)
    }
}

/**
 * With "Show only matching comments", replaces the list's items with the matches, also the ones
 * inside collapsed comments, at the end of CommentsAdapter.processData.
 */
@Export
fun onItemsProcessed(adapter: CommentsAdapter?) {
    val search = Searches.active ?: return
    if (!search.filter || search.query.isEmpty() || adapter == null) return
    try {
        if (field(search.fragment, "adapter") !== adapter) return
        @Suppress("UNCHECKED_CAST") val items = field(adapter, "items") as MutableList<Any?>
        items.clear()
        for (match in findMatches(search, search.query)) {
            val item = ThreadItem.CommentItem()
            item.comment = match
            items.add(item)
        }
        // No "loading" row at the end: "SEARCH ALL" loads the rest.
        findField(adapter.javaClass, "hasMore")?.setBoolean(adapter, false)
    } catch (ex: Exception) {
        ErrorDialog.report(Searches.TAG, "Could not show only the matching comments", ex)
    }
}

private fun open(search: Search, collapseButton: TextView) {
    try {
        val activity = search.activity
        val listView = field(search.fragment, "listView") as ListView
        val swipeLayout = field(search.fragment, "swipeLayout") as View
        val link =
            (field(field(search.fragment, "adapter"), "link") ?: field(search.fragment, "link"))
                as ThingData
        search.linkName = link.name ?: "t3_${link.id}"

        if (!search.hasBar) {
            buildBar(search, collapseButton, swipeLayout.parent as ViewGroup)
        }
        search.listView = listView
        search.sortBar = collapseButton.parent as View
        if (search.bar.visibility != View.VISIBLE) {
            search.bar.visibility = View.VISIBLE
            val positioner = ViewTreeObserver.OnPreDrawListener {
                position(search)
                true
            }
            search.positioner = positioner
            listView.viewTreeObserver.addOnPreDrawListener(positioner)
            position(search)
        }
        Searches.active = search
        search.input.requestFocus()
        val keyboard = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        keyboard.showSoftInput(search.input, InputMethodManager.SHOW_IMPLICIT)
        runSearch(search, false)
    } catch (ex: Exception) {
        ErrorDialog.report(Searches.TAG, "Could not open the comment search", ex)
    }
}

private fun close(search: Search) {
    try {
        Searches.main.removeCallbacks(search.searchRunnable)
        hideKeyboard(search)
        search.bar.visibility = View.GONE
        search.positioner?.let {
            search.listView?.viewTreeObserver?.removeOnPreDrawListener(it)
            search.positioner = null
        }
        if (Searches.active === search) Searches.active = null
        // Shows all comments again, without highlights.
        requery(search)
    } catch (ex: Exception) {
        ErrorDialog.report(Searches.TAG, "Could not close the comment search", ex)
    }
}

/**
 * Keeps the find bar over the sort bar while that's on screen, else at the top of the comments: the
 * bar never covers the post.
 */
private fun position(search: Search) {
    try {
        val listView = search.listView ?: return
        val header = field(search.fragment, "header") as View?
        var y = 0f
        var pinned = true
        if (listView.firstVisiblePosition == 0 && header != null && header.parent === listView) {
            val barLocation = IntArray(2)
            val containerLocation = IntArray(2)
            search.sortBar?.getLocationInWindow(barLocation)
            (search.bar.parent as View).getLocationInWindow(containerLocation)
            val top = (barLocation[1] - containerLocation[1]).toFloat()
            if (top > 0) {
                y = top
                pinned = false
            }
        }
        if (search.bar.translationY != y) search.bar.translationY = y
        val elevation = if (pinned) dp(search.activity, 4).toFloat() else 0f
        if (search.bar.elevation != elevation) search.bar.elevation = elevation
    } catch (ex: Exception) {
        Log.i(Searches.TAG, "Could not place the find bar", ex)
    }
}

private fun buildBar(search: Search, collapseButton: TextView, container: ViewGroup) {
    val activity = search.activity
    val secondary = collapseButton.textColors

    val bar = LinearLayout(activity)
    bar.orientation = LinearLayout.HORIZONTAL
    bar.gravity = Gravity.CENTER_VERTICAL
    val sortBar = collapseButton.parent as View
    // The sort bar's translucent color over the list's background, so the comments don't show
    // through.
    val sortBackground = sortBar.background?.constantState?.newDrawable()?.mutate()
    val listBackground = ColorDrawable(themeColor(activity, "bgColor", Color.BLACK))
    bar.background =
        if (sortBackground == null) listBackground
        else LayerDrawable(arrayOf(listBackground, sortBackground))
    // Touches don't reach the list below.
    bar.isClickable = true

    val input = EditText(activity)
    input.background = null
    input.isSingleLine = true
    input.inputType = InputType.TYPE_CLASS_TEXT
    input.imeOptions = EditorInfo.IME_ACTION_SEARCH
    input.hint = BaconReaderStrings.commentSearchHint
    input.setHintTextColor(secondary)
    input.setTextColor(primaryTextColor(activity, secondary.defaultColor))
    input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
    input.setPadding(dp(activity, 16), 0, dp(activity, 8), 0)
    input.addTextChangedListener(
        object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                Searches.main.removeCallbacks(search.searchRunnable)
                Searches.main.postDelayed(search.searchRunnable, Searches.SEARCH_DELAY_MS)
            }
        }
    )
    input.setOnEditorActionListener { _, _, _ ->
        Searches.main.removeCallbacks(search.searchRunnable)
        hideKeyboard(search)
        if (search.query != currentQuery(search)) runSearch(search, true) else move(search, 1)
        true
    }
    bar.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    search.input = input

    val counter = TextView(activity)
    counter.setTextColor(secondary)
    counter.setTextSize(TypedValue.COMPLEX_UNIT_PX, collapseButton.textSize)
    counter.typeface = collapseButton.typeface
    counter.setPadding(dp(activity, 4), 0, dp(activity, 4), 0)
    bar.addView(
        counter,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ),
    )
    search.counter = counter

    val searchAll = TextView(activity)
    searchAll.text = BaconReaderStrings.commentSearchAll
    searchAll.setTextColor(secondary)
    searchAll.setTextSize(TypedValue.COMPLEX_UNIT_PX, collapseButton.textSize)
    searchAll.typeface = collapseButton.typeface
    searchAll.gravity = Gravity.CENTER_VERTICAL
    searchAll.setPadding(dp(activity, 8), 0, dp(activity, 8), 0)
    searchAll.background = themeDrawable(activity, android.R.attr.selectableItemBackground)
    searchAll.setOnClickListener { loadAll(search) }
    bar.addView(
        searchAll,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ),
    )
    search.searchAll = searchAll

    val filterButton = ImageView(activity)
    filterButton.setImageDrawable(FunnelDrawable(dp(activity, 18)))
    filterButton.imageTintList = secondary
    filterButton.scaleType = ImageView.ScaleType.CENTER_INSIDE
    filterButton.contentDescription = BaconReaderStrings.commentSearchFilter
    filterButton.background =
        themeDrawable(activity, android.R.attr.selectableItemBackgroundBorderless)
    filterButton.setOnClickListener { toggleFilter(search) }
    bar.addView(
        filterButton,
        LinearLayout.LayoutParams(dp(activity, 36), ViewGroup.LayoutParams.MATCH_PARENT),
    )
    search.filterButton = filterButton

    bar.addView(
        iconButton(activity, "ic_menu_arrow_up_black_24dp", secondary) { move(search, -1) },
        LinearLayout.LayoutParams(dp(activity, 36), ViewGroup.LayoutParams.MATCH_PARENT),
    )
    bar.addView(
        iconButton(activity, "ic_menu_arrow_down_black_24dp", secondary) { move(search, 1) },
        LinearLayout.LayoutParams(dp(activity, 36), ViewGroup.LayoutParams.MATCH_PARENT),
    )
    bar.addView(
        iconButton(activity, "abc_ic_clear_material", secondary) { close(search) },
        LinearLayout.LayoutParams(dp(activity, 44), ViewGroup.LayoutParams.MATCH_PARENT),
    )

    bar.visibility = View.GONE
    if (container is RelativeLayout) {
        val params =
            RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 48))
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP)
        container.addView(bar, params)
    } else {
        container.addView(
            bar,
            0,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 48)),
        )
    }
    search.bar = bar
}

private fun iconButton(
    activity: Activity,
    drawable: String,
    color: ColorStateList,
    listener: View.OnClickListener,
): ImageView {
    val button = ImageView(activity)
    button.setImageDrawable(tinted(activity, drawable, color))
    button.scaleType = ImageView.ScaleType.CENTER_INSIDE
    button.background = themeDrawable(activity, android.R.attr.selectableItemBackgroundBorderless)
    button.setOnClickListener(listener)
    return button
}

private fun hideKeyboard(search: Search) {
    val keyboard =
        search.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    keyboard.hideSoftInputFromWindow(search.input.windowToken, 0)
}

private fun currentQuery(search: Search) = search.input.text.toString().trim()

/** Finds the comments with the query, and with [jump] scrolls to the first one. */
private fun runSearch(search: Search, jump: Boolean) {
    try {
        val query = currentQuery(search)
        val changed = query != search.query
        search.query = query
        val previous = search.currentComment
        search.matches = if (query.isEmpty()) emptyList() else findMatches(search, query)
        search.current = search.matches.indexOf(previous)
        if (search.current < 0) {
            search.currentComment = null
            if (jump && search.matches.isNotEmpty()) {
                search.current = 0
                search.currentComment = search.matches[0]
            }
        }
        updateCounter(search)
        val currentComment = search.currentComment
        if (currentComment != null && (changed || previous !== currentComment || search.filter)) {
            scrollTo(search, currentComment)
        } else if (search.filter) {
            requery(search)
        } else {
            refresh(search)
        }
    } catch (ex: Exception) {
        ErrorDialog.report(Searches.TAG, "Could not search the comments", ex)
    }
}

private fun move(search: Search, direction: Int) {
    val size = search.matches.size
    if (size == 0) return
    val next =
        if (search.current < 0) (if (direction > 0) 0 else size - 1)
        else (search.current + direction + size) % size
    search.current = next
    val comment = search.matches[next]
    search.currentComment = comment
    updateCounter(search)
    scrollTo(search, comment)
}

private fun updateCounter(search: Search) {
    search.counter.text =
        when {
            search.query.isEmpty() -> ""
            search.matches.isEmpty() -> "0/0"
            else -> "${maxOf(1, search.current + 1)}/${search.matches.size}"
        }
    var unloaded = 0
    try {
        unloaded = unloadedCount(search)
    } catch (ex: Exception) {
        Log.i(Searches.TAG, "Could not count the comments that aren't loaded", ex)
    }
    search.filterButton.imageTintList =
        if (search.filter) ColorStateList.valueOf(accentColor(search.activity))
        else search.searchAll.textColors
    search.searchAll.visibility = if (unloaded > 0 || search.loadingAll) View.VISIBLE else View.GONE
    search.searchAll.isEnabled = !search.loadingAll
    search.searchAll.text =
        if (search.loadingAll) BaconReaderStrings.commentSearchLoading
        else BaconReaderStrings.commentSearchAll
}

private fun findMatches(search: Search, query: String): List<Comment> =
    replies(search).filterIsInstance<Comment>().filter {
        contains(unescape(it.body), query) || contains(it.author, query)
    }

private fun contains(text: String?, query: String) =
    text != null && text.contains(query, ignoreCase = true)

/** Reddit escapes these in the markdown of comments. */
private fun unescape(body: String?) =
    body?.replace("&lt;", "<")?.replace("&gt;", ">")?.replace("&amp;", "&")

/** Expands the collapsed comments above the comment, then scrolls to it. */
private fun scrollTo(search: Search, comment: Comment) {
    try {
        val adapter = field(search.fragment, "adapter") as CommentsAdapter
        synchronized(ThreadManager::class.java) {
            val thread = thread(search)
            if (thread != null) {
                @Suppress("UNCHECKED_CAST")
                val collapsed = field(thread, "collapsedIds") as MutableSet<String?>
                val byName = HashMap<String, ThingData>()
                for (reply in thread.replies) {
                    val data = reply as? ThingData ?: continue
                    data.name?.let { byName[it] = data }
                }
                val seen = HashSet<ThingData>()
                var parent = byName[comment.parent_id]
                while (parent != null && seen.add(parent)) {
                    collapsed.remove(parent.id)
                    parent = byName[(parent as? Comment)?.parent_id]
                }
            }
        }
        // Reloading the list after scrolling would put it back where it was: reload first.
        adapter.requery()

        @Suppress("UNCHECKED_CAST") val items = field(adapter, "items") as List<Any?>
        val position = items.indexOfFirst { (it as? ThreadItem.CommentItem)?.comment === comment }
        val listView = field(search.fragment, "listView") as ListView
        if (position < 0) return
        val listPosition = position + listView.headerViewsCount
        val barHeight = dp(search.activity, 48)
        listView.post {
            listView.setSelectionFromTop(listPosition, barHeight)
            // A long comment: also bring the matching words on screen.
            listView.post { revealWord(search, listView, listPosition, barHeight) }
        }
    } catch (ex: Exception) {
        ErrorDialog.report(Searches.TAG, "Could not scroll to the comment", ex)
    }
}

private fun revealWord(search: Search, listView: ListView, listPosition: Int, barHeight: Int) {
    try {
        val item = listView.getChildAt(listPosition - listView.firstVisiblePosition) ?: return
        val body =
            item.findViewById<View>(
                search.activity.resources.getIdentifier(
                    "commentBody",
                    "id",
                    search.activity.packageName,
                )
            ) as? TextView ?: return
        val layout = body.layout ?: return
        val at = body.text.toString().indexOf(search.query, ignoreCase = true)
        if (at < 0) return
        val line = layout.getLineForOffset(at)
        var y = body.totalPaddingTop + layout.getLineTop(line)
        var view: View? = body
        while (view != null && view !== item) {
            y += view.top
            view = view.parent as? View
        }
        val lineHeight = layout.getLineBottom(line) - layout.getLineTop(line)
        val visibleBottom = listView.height - listView.paddingBottom
        if (item.top + y + lineHeight <= visibleBottom - dp(search.activity, 16)) return
        // The line a third down the visible comments.
        val target = barHeight + (visibleBottom - barHeight) / 3
        listView.setSelectionFromTop(listPosition, target - y)
    } catch (ex: Exception) {
        Log.i(Searches.TAG, "Could not scroll to the matching words", ex)
    }
}

private fun toggleFilter(search: Search) {
    search.filter = !search.filter
    updateCounter(search)
    val comment = search.currentComment
    if (comment != null) scrollTo(search, comment) else requery(search)
}

private fun requery(search: Search) {
    try {
        (field(search.fragment, "adapter") as CommentsAdapter?)?.requery()
    } catch (ex: Exception) {
        Log.i(Searches.TAG, "Could not reload the comments", ex)
    }
}

/** Redraws the comments, with or without highlights. */
private fun refresh(search: Search) {
    try {
        (field(search.fragment, "adapter") as CommentsAdapter?)?.notifyDataSetChanged()
    } catch (ex: Exception) {
        Log.i(Searches.TAG, "Could not redraw the comments", ex)
    }
}

/** Loads every "load more comments" of the post, one request at a time, then searches again. */
private fun loadAll(search: Search) {
    if (search.loadingAll) return
    search.loadingAll = true
    updateCounter(search)
    loadNext(search, HashSet(), 0)
}

private fun loadNext(search: Search, tried: MutableSet<String?>, requests: Int) {
    try {
        if (Searches.active !== search || search.bar.visibility != View.VISIBLE) {
            search.loadingAll = false
            return
        }
        val adapter = field(search.fragment, "adapter") as CommentsAdapter
        // The list loads its last "more" by itself when scrolled to the end: never load it twice.
        if (field(adapter, "loading") == true) {
            Searches.main.postDelayed({ loadNext(search, tried, requests) }, 500)
            return
        }
        var more: More? = null
        if (requests < Searches.MAX_LOAD_REQUESTS) {
            val loading =
                synchronized(ThreadManager::class.java) {
                    HashSet(thread(search)?.states?.keys ?: emptySet())
                }
            more =
                replies(search).filterIsInstance<More>().firstOrNull {
                    !it.children.isNullOrEmpty() && it.id !in tried && it.id !in loading
                }
        }
        if (more == null) {
            search.loadingAll = false
            runSearch(search, true)
            return
        }
        tried.add(more.id)

        val threadKey = field(search.fragment, "threadKey") as ThreadKey?
        ThreadManager.loadMore(
            search.activity,
            adapter,
            threadKey,
            more,
            object : Tasks.OnCompleteListener<Any?>() {
                override fun onComplete(data: Any?) {
                    requery(search)
                    updateCounter(search)
                    loadNext(search, tried, requests + 1)
                }

                override fun onCancel(error: String?) {
                    search.loadingAll = false
                    Toast.makeText(
                            search.activity,
                            BaconReaderStrings.commentSearchError(error ?: ""),
                            Toast.LENGTH_LONG,
                        )
                        .show()
                    runSearch(search, true)
                }
            },
        )
    } catch (ex: Exception) {
        search.loadingAll = false
        updateCounter(search)
        ErrorDialog.report(Searches.TAG, "Could not load all comments", ex)
    }
}

/** Comments behind "load more comments", not "continue this thread", which is another page. */
private fun unloadedCount(search: Search): Int =
    replies(search).filterIsInstance<More>().sumOf {
        val children = it.children
        if (children.isNullOrEmpty()) 0 else maxOf(children.size, it.count)
    }

/** ThreadManager's thread of the post: its comments and "more" items in order. */
private fun thread(search: Search): ThreadManager.Thread? {
    val threadKey = field(search.fragment, "threadKey")
    val threads = findField(ThreadManager::class.java, "threads")?.get(null) as Map<*, *>?
    return threads?.get(threadKey) as ThreadManager.Thread?
}

private fun replies(search: Search): List<Any?> =
    synchronized(ThreadManager::class.java) { ArrayList(thread(search)?.replies ?: emptyList()) }

// The fragment's views and adapter, the adapter's items and the thread's collapsed ids are
// private or protected in BaconReader.
private fun field(target: Any?, name: String): Any? {
    if (target == null) return null
    val field = findField(target.javaClass, name) ?: throw NoSuchFieldException(name)
    return field.get(target)
}

private fun findField(type: Class<*>, name: String): Field? {
    val key = "${type.name}.$name"
    synchronized(Searches.fields) {
        if (Searches.fields.containsKey(key)) return Searches.fields[key]
    }
    var found: Field? = null
    var current: Class<*>? = type
    while (current != null && found == null) {
        try {
            found = current.getDeclaredField(name).apply { isAccessible = true }
        } catch (ignored: NoSuchFieldException) {}
        current = current.superclass
    }
    synchronized(Searches.fields) { Searches.fields[key] = found }
    return found
}

private fun tinted(activity: Activity, name: String, color: ColorStateList): Drawable? {
    val id = activity.resources.getIdentifier(name, "drawable", activity.packageName)
    if (id == 0) return null
    return activity.getDrawable(id)?.mutate()?.apply { setTintList(color) }
}

private fun themeDrawable(activity: Activity, attribute: Int): Drawable? {
    val value = TypedValue()
    if (!activity.theme.resolveAttribute(attribute, value, true) || value.resourceId == 0) {
        return null
    }
    return activity.getDrawable(value.resourceId)?.mutate()
}

private fun primaryTextColor(activity: Activity, fallback: Int) =
    styledColor(activity, android.R.attr.textColorPrimary, fallback)

private fun accentColor(activity: Activity) =
    styledColor(activity, android.R.attr.colorAccent, 0xFFFF8B60.toInt())

private fun styledColor(activity: Activity, attribute: Int, fallback: Int): Int {
    val array = activity.theme.obtainStyledAttributes(intArrayOf(attribute))
    try {
        return array.getColor(0, fallback)
    } finally {
        array.recycle()
    }
}

private fun themeColor(activity: Activity, attributeName: String, fallback: Int): Int {
    val attribute = activity.resources.getIdentifier(attributeName, "attr", activity.packageName)
    val value = TypedValue()
    if (attribute == 0 || !activity.theme.resolveAttribute(attribute, value, true)) return fallback
    if (value.resourceId != 0) return activity.getColor(value.resourceId)
    return value.data
}

private fun withAlpha(color: Int, alpha: Int) =
    Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

private fun dp(context: Context, value: Int) =
    Math.round(
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics,
        )
    )

/** A funnel, for "Show only matching comments": the app has no filter icon. */
private class FunnelDrawable(private val size: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var tint = ColorStateList.valueOf(Color.GRAY)

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val left = bounds.left.toFloat()
        val top = bounds.top.toFloat()
        path.reset()
        path.moveTo(left + w * 0.08f, top + h * 0.15f)
        path.lineTo(left + w * 0.92f, top + h * 0.15f)
        path.lineTo(left + w * 0.58f, top + h * 0.52f)
        path.lineTo(left + w * 0.58f, top + h * 0.85f)
        path.lineTo(left + w * 0.42f, top + h * 0.75f)
        path.lineTo(left + w * 0.42f, top + h * 0.52f)
        path.close()
        paint.color = tint.getColorForState(state, tint.defaultColor)
        canvas.drawPath(path, paint)
    }

    override fun setTintList(tint: ColorStateList?) {
        if (tint != null) this.tint = tint
        invalidateSelf()
    }

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        invalidateSelf()
        return true
    }

    override fun getIntrinsicWidth() = size

    override fun getIntrinsicHeight() = size

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
