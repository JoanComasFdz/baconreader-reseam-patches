// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("FlairFilter")

package com.joancomasfdz.baconreader.flairs

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.common.ErrorDialog
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.onelouder.baconreader.connectivity.Tasks
import com.onelouder.baconreader.data.LinksetKey
import com.onelouder.baconreader.reddit.Link
import com.onelouder.baconreader.reddit.LinkFlair
import com.onelouder.baconreader.reddit.Listing
import com.onelouder.baconreader.reddit.RedditApi
import java.util.Locale

/**
 * A "Flair" control in the sort bar of the post list. The chosen flairs are applied as a Reddit
 * search in the subreddit (`flair_name:"A" OR flair_name:"B"`), which the app already loads like
 * any listing when its LinksetKey has a query.
 */
private object Flairs {
    const val TAG = "FlairFilter"
    const val CONTROL_TAG = "baconreader_patches_flair_filter"

    /** Subreddit (lower case) to the flairs seen in loaded posts, in order of appearance. */
    val seen = HashMap<String, LinkedHashSet<String>>()

    /** Subreddit (lower case) to the flairs from api/link_flair. */
    val api = HashMap<String, List<String>>()

    /** The queries this patch built, to their flairs. */
    val queries = HashMap<String, List<String>>()
}

/** Remembers the flairs of the loaded posts: the list for subreddits whose flairs are private. */
@Export
fun onListingLoaded(listing: Listing?) {
    try {
        val links = listing?.getChildren(Link::class.java) ?: return
        synchronized(Flairs.seen) {
            for (link in links) {
                val subreddit = link?.subreddit ?: continue
                val flair = clean(link.link_flair_text) ?: continue
                Flairs.seen.getOrPut(key(subreddit)) { LinkedHashSet() }.add(flair)
            }
        }
    } catch (ex: Exception) {
        Log.i(Flairs.TAG, "Could not read the flairs of the loaded posts", ex)
    }
}

/** Shows the control, with the flairs of the feed's query, whenever a feed opens. */
@Export
fun onOpenLinkset(activity: Activity?) {
    if (activity == null) return
    try {
        val control = control(activity) ?: return
        val linksetKey = linksetKey(activity)
        if (subreddit(linksetKey) == null) {
            control.visibility = View.GONE
            return
        }
        control.visibility = View.VISIBLE
        val selected = selected(linksetKey)
        val textColor = textColor(activity)
        if (selected.isEmpty()) {
            control.text = BaconReaderStrings.flairFilterButton
            control.contentDescription = BaconReaderStrings.flairFilterContentDescription
            control.setTextColor(textColor)
        } else {
            val flairs = TextUtils.join(", ", selected)
            control.text = flairs
            control.contentDescription =
                BaconReaderStrings.flairFilterContentDescriptionChosen(flairs)
            control.setTextColor(accentColor(activity, textColor))
        }
        for (drawable in control.compoundDrawablesRelative) {
            drawable?.setTintList(ColorStateList.valueOf(control.currentTextColor))
        }
    } catch (ex: Exception) {
        ErrorDialog.report(Flairs.TAG, "Could not update the flair filter", ex)
    }
}

/** The control, added to the sort bar the first time. */
private fun control(activity: Activity): TextView? {
    val bar =
        activity.findViewById<View>(id(activity, "sortLayout")) as? RelativeLayout ?: return null
    (bar.findViewWithTag<View>(Flairs.CONTROL_TAG) as? TextView)?.let {
        return it
    }

    val sortTitle = activity.findViewById<View>(id(activity, "sortTitle")) as? TextView
    val control = TextView(activity)
    control.tag = Flairs.CONTROL_TAG
    control.id = View.generateViewId()
    // The same text as the sort title ("What's hot"): the size, color and font of the app's theme.
    if (sortTitle != null) {
        control.setTextSize(TypedValue.COMPLEX_UNIT_PX, sortTitle.textSize)
        control.setTextColor(sortTitle.textColors)
        control.typeface = sortTitle.typeface
    }
    control.isSingleLine = true
    control.ellipsize = TextUtils.TruncateAt.END
    control.maxWidth = activity.resources.displayMetrics.widthPixels * 2 / 5
    control.gravity = Gravity.CENTER_VERTICAL
    control.setPadding(dp(activity, 12), 0, dp(activity, 4), 0)
    themeDrawable(activity, attr(activity, "ic_arrow_dropdown"))?.let { arrow ->
        val size = Math.round(control.textSize * 1.4f)
        arrow.setBounds(0, 0, size, size)
        control.setCompoundDrawablesRelative(null, null, arrow, null)
    }
    control.background = themeDrawable(activity, android.R.attr.selectableItemBackground)
    control.setOnClickListener { showDialog(activity) }

    val params =
        RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    params.addRule(RelativeLayout.ALIGN_PARENT_END)
    // Left of the sort icon: its 16dp margin and 24dp icon.
    params.marginEnd = dp(activity, 48)
    bar.addView(control, params)

    // The sort title ends where the control starts.
    if (sortTitle != null) {
        sortTitle.isSingleLine = true
        sortTitle.ellipsize = TextUtils.TruncateAt.END
        val titleParams = sortTitle.layoutParams as RelativeLayout.LayoutParams
        titleParams.addRule(RelativeLayout.START_OF, control.id)
        sortTitle.layoutParams = titleParams
    }
    return control
}

private fun showDialog(activity: Activity) {
    try {
        val linksetKey = linksetKey(activity)
        val subreddit = subreddit(linksetKey) ?: return
        synchronized(Flairs.api) {
            if (Flairs.api.containsKey(key(subreddit))) {
                showDialog(activity, linksetKey, subreddit)
                return
            }
        }
        RedditApi.getLinkFlairs(
            activity,
            subreddit,
            object : Tasks.OnCompleteListener<List<LinkFlair>>() {
                override fun onComplete(flairs: List<LinkFlair>?) {
                    val names = ArrayList<String>()
                    flairs?.forEach { flair ->
                        val name = clean(flair?.text)
                        if (name != null && name !in names) names.add(name)
                    }
                    done(names)
                }

                override fun onCancel(error: String?) {
                    // Only subreddits that let users choose their flair list them. The others
                    // use the flairs seen in posts.
                    Log.i(Flairs.TAG, "api/link_flair failed for $subreddit: $error")
                    done(emptyList())
                }

                private fun done(names: List<String>) {
                    synchronized(Flairs.api) { Flairs.api[key(subreddit)] = names }
                    activity.runOnUiThread {
                        try {
                            if (!activity.isFinishing) showDialog(activity, linksetKey, subreddit)
                        } catch (ex: Exception) {
                            ErrorDialog.report(Flairs.TAG, "Could not show the flair filter", ex)
                        }
                    }
                }
            },
        )
    } catch (ex: Exception) {
        ErrorDialog.report(Flairs.TAG, "Could not show the flair filter", ex)
    }
}

private fun showDialog(activity: Activity, linksetKey: LinksetKey?, subreddit: String) {
    val selected = selected(linksetKey)
    val flairs = ArrayList(selected)
    synchronized(Flairs.api) {
        Flairs.api[key(subreddit)]?.forEach { if (it !in flairs) flairs.add(it) }
    }
    synchronized(Flairs.seen) {
        Flairs.seen[key(subreddit)]?.forEach { if (it !in flairs) flairs.add(it) }
    }

    val builder = AlertDialog.Builder(activity).setTitle(BaconReaderStrings.flairFilterTitle)
    if (flairs.isEmpty()) {
        builder
            .setMessage(BaconReaderStrings.flairFilterEmpty)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        return
    }

    val checked = BooleanArray(flairs.size) { flairs[it] in selected }
    builder
        .setMultiChoiceItems(flairs.toTypedArray<CharSequence>(), checked) { _, which, isChecked ->
            checked[which] = isChecked
        }
        .setPositiveButton(BaconReaderStrings.flairFilterApply) { _, _ ->
            apply(activity, flairs.filterIndexed { i, _ -> checked[i] })
        }
        .setNeutralButton(BaconReaderStrings.flairFilterClear) { _, _ ->
            apply(activity, emptyList())
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}

/** Opens the feed again with the flair search, or without it when nothing is chosen. */
private fun apply(activity: Activity, flairs: List<String>) {
    try {
        val linksetKey = linksetKey(activity) ?: return
        if (flairs == selected(linksetKey)) return

        var query: String? = null
        var sort = linksetKey.sort
        if (flairs.isNotEmpty()) {
            query = flairs.joinToString(" OR ") { "flair_name:\"${it.replace("\"", "")}\"" }
            synchronized(Flairs.queries) { Flairs.queries[query] = ArrayList(flairs) }
            // Search has no "best" sort, which the key stores as null: use hot.
            if (sort == null) sort = "hot"
        }

        val newKey = LinksetKey(linksetKey.redditId, sort, linksetKey.sortTime, query)
        linksetKeyField(activity).set(activity, newKey)
        val openLinkset = activity.javaClass.getDeclaredMethod("openLinkset")
        openLinkset.isAccessible = true
        openLinkset.invoke(activity)
    } catch (ex: Exception) {
        ErrorDialog.report(Flairs.TAG, "Could not apply the flair filter", ex)
    }
}

/** The flairs of the feed's query, if this patch built it. */
private fun selected(linksetKey: LinksetKey?): List<String> {
    val query = linksetKey?.query ?: return ArrayList()
    synchronized(Flairs.queries) {
        return ArrayList(Flairs.queries[query] ?: emptyList())
    }
}

// MainActivity keeps the feed's key in a private field, and opens it with a protected method.
private fun linksetKeyField(activity: Activity) =
    activity.javaClass.getDeclaredField("linksetKey").apply { isAccessible = true }

private fun linksetKey(activity: Activity): LinksetKey? =
    linksetKeyField(activity).get(activity) as LinksetKey?

/** The subreddit of the feed, or null for Front Page, All, Popular, Friends and multireddits. */
private fun subreddit(linksetKey: LinksetKey?): String? {
    val redditId = linksetKey?.redditId ?: return null
    if (!redditId.isPureSubreddit) return null
    val name = redditId.name
    if (name == null || name.equals("popular", ignoreCase = true)) return null
    return name
}

private fun key(subreddit: String) = subreddit.lowercase(Locale.ROOT)

private fun clean(flair: String?): String? = flair?.trim()?.takeIf { it.isNotEmpty() }

private fun textColor(activity: Activity): Int {
    val sortTitle = activity.findViewById<View>(id(activity, "sortTitle")) as? TextView
    return sortTitle?.currentTextColor ?: 0xFF888888.toInt()
}

private fun accentColor(activity: Activity, fallback: Int): Int {
    val array = activity.theme.obtainStyledAttributes(intArrayOf(android.R.attr.colorAccent))
    try {
        return array.getColor(0, fallback)
    } finally {
        array.recycle()
    }
}

private fun id(activity: Activity, name: String) =
    activity.resources.getIdentifier(name, "id", activity.packageName)

private fun attr(activity: Activity, name: String) =
    activity.resources.getIdentifier(name, "attr", activity.packageName)

private fun themeDrawable(activity: Activity, attribute: Int): Drawable? {
    if (attribute == 0) return null
    val value = TypedValue()
    if (!activity.theme.resolveAttribute(attribute, value, true) || value.resourceId == 0) {
        return null
    }
    return activity.getDrawable(value.resourceId)?.mutate()
}

private fun dp(activity: Activity, value: Int) =
    Math.round(
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            activity.resources.displayMetrics,
        )
    )
