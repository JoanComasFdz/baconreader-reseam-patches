// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.editor

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.widget.EditText
import android.widget.TextView
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.onelouder.baconreader.RequestMethod
import com.onelouder.baconreader.data.SessionManager
import com.onelouder.baconreader.data.SessionToken
import com.onelouder.baconreader.reddit.RedditApi
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.min
import org.json.JSONObject

/**
 * "1,234 / 10,000" next to the format bar: Reddit's limit for the text being written (comments and
 * messages 10,000 characters, posts 40,000), or the subreddit's own post requirements for a text
 * post. Orange from 90% of the limit or below the subreddit's minimum, red over the limit.
 */
internal class CharacterCounter(
    private val editText: EditText,
    private val normalColor: Int,
    private val warningColor: Int,
) {
    val view = TextView(editText.context)
    private var max: Int
    private var min = 0
    private var textNotAllowed = false
    private var subreddit: String? = null

    init {
        val context = editText.context
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        view.gravity = Gravity.CENTER_VERTICAL or Gravity.END
        val padding = dp(context, 8)
        view.setPadding(padding, 0, padding * 2, 0)

        max = if (isPost(context)) POST_LIMIT else COMMENT_LIMIT
        editText.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    update()
                }
            }
        )
        followSubreddit()
        update()
    }

    private fun update() {
        val text = editText.text.toString()
        // Reddit counts characters, not UTF-16 units: an emoji is one.
        val count = text.codePointCount(0, text.length)
        val format = NumberFormat.getIntegerInstance()
        if (textNotAllowed) {
            view.text = BaconReaderStrings.markdownCounterNotAllowed(subreddit.orEmpty())
            view.setTextColor(if (count > 0) RED else warningColor)
            return
        }
        view.text =
            if (min > 0 && count < min) {
                BaconReaderStrings.markdownCounterMin(
                    format.format(count),
                    format.format(max),
                    format.format(min),
                )
            } else {
                BaconReaderStrings.markdownCounter(format.format(count), format.format(max))
            }
        view.setTextColor(
            when {
                count > max -> RED
                count >= max * 9 / 10 || (min > 0 && count > 0 && count < min) -> warningColor
                else -> normalColor
            }
        )
    }

    /** Text posts: the subreddit's own limits, when it has some, whenever the subreddit changes. */
    private fun followSubreddit() {
        val context = editText.context
        if (context !is Activity || !context.javaClass.name.endsWith(".SubmitActivity")) return
        val field = editText.rootView.findViewById<android.view.View>(id(context, "storySR"))
        if (field !is EditText) return
        val load = Runnable { loadRequirements(field.text.toString()) }
        field.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    MAIN.removeCallbacks(load)
                    MAIN.postDelayed(load, REQUIREMENTS_DELAY_MS)
                }
            }
        )
        load.run()
    }

    private fun loadRequirements(name: String) {
        val subreddit = name.trim().replaceFirst(SUBREDDIT_PREFIX, "")
        if (!subreddit.matches(SUBREDDIT_NAME)) {
            apply(null, null)
            return
        }
        val key = subreddit.lowercase(Locale.ROOT)
        val cached = synchronized(REQUIREMENTS) { REQUIREMENTS[key] }
        if (cached != null) {
            apply(subreddit, cached)
            return
        }
        LOADER.execute {
            var requirements: IntArray? = null
            try {
                val json =
                    JSONObject(apiGet(editText.context, "api/v1/$subreddit/post_requirements"))
                requirements =
                    intArrayOf(
                        json.optInt("body_text_max_length", 0),
                        json.optInt("body_text_min_length", 0),
                        if (json.optString("body_restriction_policy") == "notAllowed") 1 else 0,
                    )
                synchronized(REQUIREMENTS) { REQUIREMENTS[key] = requirements }
            } catch (t: Exception) {
                // No such subreddit yet, while still typing, or offline: Reddit's general limit.
                Log.i(TAG, "Could not load the post requirements of r/$subreddit", t)
            }
            val loaded = requirements
            MAIN.post { apply(subreddit, loaded) }
        }
    }

    private fun apply(subreddit: String?, requirements: IntArray?) {
        this.subreddit = subreddit
        max = POST_LIMIT
        min = 0
        textNotAllowed = false
        if (requirements != null) {
            if (requirements[0] > 0) max = min(POST_LIMIT, requirements[0])
            min = requirements[1]
            textNotAllowed = requirements[2] == 1
        }
        update()
    }

    private companion object {
        const val TAG = "CharacterCounter"
        const val COMMENT_LIMIT = 10_000
        const val POST_LIMIT = 40_000
        const val RED = 0xFFE53935.toInt()
        const val REQUIREMENTS_DELAY_MS = 800L

        val SUBREDDIT_PREFIX = Regex("^/?r/")
        val SUBREDDIT_NAME = Regex("[A-Za-z0-9_]{2,21}")

        val MAIN = Handler(Looper.getMainLooper())
        val LOADER = Executors.newSingleThreadExecutor()

        /** Subreddit, in lower case, to its post requirements, for the session. */
        val REQUIREMENTS = HashMap<String, IntArray>()

        /** A text post, or the edit of a post (PostEditActivity with a link): 40,000. */
        fun isPost(context: Context): Boolean {
            if (context !is Activity) return false
            val name = context.javaClass.name
            if (name.endsWith(".SubmitActivity")) return true
            if (!name.endsWith(".PostEditActivity")) return false
            try {
                var type: Class<*>? = context.javaClass
                while (type != null) {
                    try {
                        // PostActivity's protected field, set when a post is edited.
                        val link = type.getDeclaredField("link").apply { isAccessible = true }
                        return link.get(context) != null
                    } catch (_: NoSuchFieldException) {}
                    type = type.superclass
                }
            } catch (t: Exception) {
                Log.i(TAG, "Could not tell a post edit from a comment edit", t)
            }
            return false
        }

        /** A GET through the app's own request code, with its login and user agent. */
        fun apiGet(context: Context, path: String): String {
            val token = SessionManager.getToken()
            // RedditApi's private request method.
            val httpRequest =
                RedditApi::class
                    .java
                    .getDeclaredMethod(
                        "httpRequest",
                        Context::class.java,
                        RequestMethod::class.java,
                        String::class.java,
                        Map::class.java,
                        SessionToken::class.java,
                    )
                    .apply { isAccessible = true }
            // Without a login the app calls www.reddit.com, which needs the .json suffix.
            val url = if (token == null) "$path.json" else path
            return httpRequest.invoke(
                null,
                context.applicationContext,
                RequestMethod.GET,
                url,
                HashMap<String, String>(),
                token,
            ) as String
        }
    }
}
