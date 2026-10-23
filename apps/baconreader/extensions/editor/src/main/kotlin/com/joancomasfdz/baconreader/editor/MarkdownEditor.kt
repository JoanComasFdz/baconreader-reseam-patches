// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("MarkdownEditor")

package com.joancomasfdz.baconreader.editor

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.common.ErrorDialog
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "MarkdownEditor"
private const val ATTACHED_TAG = "baconreader_patches_markdown_editor"

/**
 * The replied comment or post takes at most this part of the reply screen; the text box the rest.
 */
private const val REPLIED_MAX_HEIGHT = 0.35f

/**
 * Called after PostActivity.onCreate (comments, replies, edits) and ComposeMessageActivity.onCreate
 * (messages) set their layout. Sets the editor up once onCreate has finished, since the app makes
 * its own format bar after the layout.
 */
@Export
fun onEditorScreen(activity: Activity?) {
    if (activity == null) return
    activity.window.decorView.post {
        try {
            val editText =
                activity.findViewById<View>(id(activity, "replyText")) as? EditText
                    ?: activity.findViewById<View>(id(activity, "messageText")) as? EditText
                    ?: return@post
            removeAppFormatBar(activity.findViewById(id(activity, "formatContainer")))
            attach(editText)
        } catch (t: Exception) {
            ErrorDialog.report(TAG, "Could not set up the markdown editor", t)
        }
    }
}

/** Called before SubmitTextPage.onCreateView returns its view (text posts). */
@Export
fun onEditorView(view: View?) {
    if (view == null) return
    try {
        val editText = view.findViewById<View>(id(view.context, "replyText")) as? EditText ?: return
        attach(editText)
        // The app's format bar belongs to the activity: removed once the page is in it.
        view.post {
            val activity = view.context as? Activity ?: return@post
            removeAppFormatBar(activity.findViewById(id(activity, "formatContainer")))
        }
    } catch (t: Exception) {
        ErrorDialog.report(TAG, "Could not set up the markdown editor", t)
    }
}

/**
 * Called after onCreateOptionsMenu of the editors inflates the menu. The bar replaces the app's
 * format button ("A"). Hiding it doesn't last, since the app shows it again, so it also gets an
 * empty, disabled view.
 */
@Export
fun hideFormatButton(activity: Activity?, menu: Menu?) {
    if (activity == null || menu == null) return
    try {
        val item = menu.findItem(id(activity, "action_format")) ?: return
        item.actionView = View(activity).apply { layoutParams = ViewGroup.LayoutParams(0, 0) }
        item.isEnabled = false
        item.isVisible = false
    } catch (t: Exception) {
        ErrorDialog.report(TAG, "Could not hide the format button", t)
    }
}

private fun removeAppFormatBar(formatContainer: View?) {
    if (formatContainer !is ViewGroup) return
    formatContainer.removeAllViews()
    formatContainer.visibility = View.GONE
}

private fun attach(editText: EditText) {
    if (editText.tag == ATTACHED_TAG) return
    editText.tag = ATTACHED_TAG
    val context = editText.context

    // Live formatting.
    val highlighter =
        MarkdownHighlighter(
            editText.currentTextColor,
            accentColor(context, editText.currentTextColor),
        )
    editText.addTextChangedListener(
        object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (s != null) highlighter.highlight(s)
            }
        }
    )
    editText.text?.let { highlighter.highlight(it) }

    // As tall as possible, the text from the top.
    editText.maxLines = Int.MAX_VALUE
    editText.gravity = Gravity.TOP or Gravity.START

    val bar = formatBar(editText)
    val parent = editText.parent as ViewGroup
    if (parent.id != 0 && parent.id == id(context, "replyTextContainer")) {
        layoutReplyScreen(editText, parent, bar)
        showScrollbar(editText) // The box scrolls its text.
    } else {
        insertAbove(parent, editText, bar)
        fillScrollView(editText)
    }
}

/**
 * The reply screen (make_comment): the replied comment scrolls above, in at most a third of the
 * screen, and the bar and the text box fill the rest, instead of a box of at most 8 lines at the
 * bottom.
 */
private fun layoutReplyScreen(editText: EditText, container: ViewGroup, bar: View) {
    container.addView(
        bar,
        0,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ),
    )
    val editParams = editText.layoutParams as LinearLayout.LayoutParams
    editParams.height = 0
    editParams.weight = 1f
    editText.layoutParams = editParams

    val activity = editText.context as? Activity ?: return
    val scroll =
        activity.findViewById<View>(id(activity, "scrollContainer")) as? ScrollView ?: return
    val containerParams = container.layoutParams as? RelativeLayout.LayoutParams ?: return
    containerParams.height = ViewGroup.LayoutParams.MATCH_PARENT
    containerParams.addRule(RelativeLayout.BELOW, scroll.id)
    container.layoutParams = containerParams

    val scrollParams = scroll.layoutParams as RelativeLayout.LayoutParams
    scrollParams.removeRule(RelativeLayout.ABOVE)
    scrollParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
    scroll.layoutParams = scrollParams

    // Long replied texts scroll within a third of the screen, also with the keyboard open.
    (container.parent as View).addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
        if (scroll.childCount == 0) return@addOnLayoutChangeListener
        val limit = ((bottom - top) * REPLIED_MAX_HEIGHT).roundToInt()
        val content = scroll.getChildAt(0).measuredHeight
        val height = if (content > limit) limit else ViewGroup.LayoutParams.WRAP_CONTENT
        val params = scroll.layoutParams as RelativeLayout.LayoutParams
        if (params.height != height) {
            params.height = height
            scroll.post { scroll.layoutParams = params }
        }
    }
}

/** Messages and text posts: the box grows until the scrolling page fills the screen. */
private fun fillScrollView(editText: EditText) {
    editText.post {
        var parent = editText.parent
        while (parent != null && parent !is ScrollView) parent = parent.parent
        val scroll = parent as? ScrollView
        if (scroll == null) {
            editText.minLines = 8
            return@post
        }
        showScrollbar(scroll) // The page scrolls, with the box in it.
        val minimum = editText.lineHeight * 4
        scroll.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (scroll.childCount == 0) return@addOnLayoutChangeListener
            val free =
                scroll.height -
                    scroll.paddingTop -
                    scroll.paddingBottom -
                    scroll.getChildAt(0).height
            val height = max(minimum, editText.height + free)
            if (abs(height - editText.minHeight) > 1) editText.post { editText.minHeight = height }
        }
        scroll.requestLayout()
    }
}

/**
 * A scrollbar on the right that stays while there is more text than fits, to show how much text
 * there is. Android draws none when everything fits.
 */
private fun showScrollbar(view: View) {
    val context = view.context
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val thumb =
            GradientDrawable().apply {
                setColor((secondaryTextColor(context) and 0x00FFFFFF) or 0x99000000.toInt())
                cornerRadius = dp(context, 2).toFloat()
                setSize(dp(context, 4), dp(context, 32))
            }
        // Also sets up scrollbars for a view without them.
        view.verticalScrollbarThumbDrawable = thumb
    }
    view.isVerticalScrollBarEnabled = true
    view.isScrollbarFadingEnabled = false
    view.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
}

private fun insertAbove(parent: ViewGroup, editText: EditText, bar: View) {
    val editParams = editText.layoutParams
    when {
        parent is LinearLayout ->
            parent.addView(
                bar,
                parent.indexOfChild(editText),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        parent is RelativeLayout && editParams is RelativeLayout.LayoutParams -> {
            // The bar takes the box's place, and the box goes below it.
            val barParams =
                RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            editParams.rules.forEachIndexed { verb, subject ->
                if (subject != 0) barParams.addRule(verb, subject)
            }
            barParams.setMargins(
                editParams.leftMargin,
                editParams.topMargin,
                editParams.rightMargin,
                0,
            )
            bar.id = View.generateViewId()
            parent.addView(bar, barParams)
            editParams.removeRule(RelativeLayout.ALIGN_PARENT_TOP)
            editParams.addRule(RelativeLayout.BELOW, bar.id)
            editParams.topMargin = 0
            editText.layoutParams = editParams
        }
        else -> parent.addView(bar, parent.indexOfChild(editText))
    }
}

private fun formatBar(editText: EditText): View {
    val context = editText.context
    val buttons =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
    val s = BaconReaderStrings
    buttons.add(styled("B", StyleSpan(Typeface.BOLD)), s.markdownBold) {
        MarkdownActions.wrap(editText, "**", "**")
    }
    buttons.add(styled("I", StyleSpan(Typeface.ITALIC)), s.markdownItalic) {
        MarkdownActions.wrap(editText, "*", "*")
    }
    buttons.add(styled("S", StrikethroughSpan()), s.markdownStrikethrough) {
        MarkdownActions.wrap(editText, "~~", "~~")
    }
    buttons.add("x²", s.markdownSuperscript) { MarkdownActions.wrap(editText, "^(", ")") }
    buttons.add(">!", s.markdownSpoiler) { MarkdownActions.wrap(editText, ">!", "!<") }
    buttons.add("</>", s.markdownCode) { MarkdownActions.wrap(editText, "`", "`") }
    buttons.add("🔗", s.markdownLink) { MarkdownActions.link(editText) }
    buttons.add("H", s.markdownHeading) { MarkdownActions.heading(editText) }
    buttons.add("•", s.markdownBullets) { MarkdownActions.prefixLines(editText, "* ", false) }
    buttons.add("1.", s.markdownNumbers) { MarkdownActions.prefixLines(editText, "", true) }
    buttons.add("❝", s.markdownQuote) { MarkdownActions.prefixLines(editText, "> ", false) }
    buttons.add("{ }", s.markdownCodeBlock) { MarkdownActions.prefixLines(editText, "    ", false) }
    buttons.add("▦", s.markdownTable) {
        val column = s.markdownTableColumn
        MarkdownActions.block(editText, "| $column | $column |\n|---|---|\n|  |  |")
    }
    buttons.add("—", s.markdownRule) { MarkdownActions.block(editText, "---") }

    val scroll =
        HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            addView(buttons)
        }

    // The buttons scroll; the character counter stays at the end of the row.
    val row =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
    row.addView(scroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    val counter =
        CharacterCounter(
            editText,
            secondaryTextColor(context),
            accentColor(context, editText.currentTextColor),
        )
    row.addView(
        counter.view,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ),
    )
    return row
}

private fun styled(label: String, span: Any): CharSequence =
    SpannableString(label).apply {
        setSpan(span, 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

private fun LinearLayout.add(label: CharSequence, description: String, action: () -> Unit) {
    val button =
        TextView(context).apply {
            text = label
            contentDescription = description
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(secondaryTextColor(context))
            gravity = Gravity.CENTER
            minWidth = dp(context, 44)
            val padding = dp(context, 10)
            setPadding(padding, padding, padding, padding)
            background = themeDrawable(context, android.R.attr.selectableItemBackgroundBorderless)
            setOnClickListener {
                try {
                    action()
                } catch (t: Exception) {
                    ErrorDialog.report(TAG, "Could not apply the format", t)
                }
            }
        }
    addView(
        button,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ),
    )
}

/** A view id of BaconReader's layouts, by name. */
internal fun id(context: Context, name: String): Int =
    context.resources.getIdentifier(name, "id", context.packageName)

private fun secondaryTextColor(context: Context): Int {
    val attribute =
        context.resources.getIdentifier("secondaryTextColor", "attr", context.packageName)
    val value = TypedValue()
    if (attribute != 0 && context.theme.resolveAttribute(attribute, value, true)) {
        return if (value.resourceId != 0) context.getColor(value.resourceId) else value.data
    }
    return 0xFF888888.toInt()
}

private fun accentColor(context: Context, fallback: Int): Int {
    val array = context.theme.obtainStyledAttributes(intArrayOf(android.R.attr.colorAccent))
    try {
        return array.getColor(0, fallback)
    } finally {
        array.recycle()
    }
}

private fun themeDrawable(context: Context, attribute: Int): Drawable? {
    val value = TypedValue()
    if (!context.theme.resolveAttribute(attribute, value, true) || value.resourceId == 0) {
        return null
    }
    return context.getDrawable(value.resourceId)
}

internal fun dp(context: Context, value: Int): Int =
    TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics,
        )
        .roundToInt()
