// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.chat

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.ViewFlipper
import com.google.android.material.appbar.AppBarLayout
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import com.onelouder.baconreader.ToolbarActivity
import java.util.Locale

/** The ids of MainActivity's views the chat goes between, from the app's resources. */
internal class MainViews(
    val listLayout: Int,
    val adContainer: Int,
    val toolbar: Int,
    val toolbarTitle: Int,
    val toolbarFlipper: Int,
    val appBar: Int,
    val refreshItem: Int,
)

/**
 * Reddit's mobile website chat, www.reddit.com/chat, in a WebView over MainActivity's post list,
 * below its toolbar, so the toolbar and the drawer stay. Users log in to Reddit's website here
 * once; its cookies stay in the WebView's cookie store. The WebView lives as long as the activity,
 * hidden while a post list shows.
 */
internal class ChatPanel(private val activity: Activity, private val views: MainViews) {
    private val root = FrameLayout(activity)
    private val progress =
        ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
    private val errorText = TextView(activity)
    private val errorView = errorView()
    private var webView: WebView? = null
    private var pendingFiles: ValueCallback<Array<Uri>>? = null
    private var failed = false
    private var attached = false

    private var savedTitle: CharSequence? = null
    private var savedFlipperChild = 0
    private var savedSoftInputMode: Int? = null

    var showing = false
        private set

    init {
        root.setBackgroundColor(background())
        // Keeps taps from reaching the post list underneath.
        root.isClickable = true
        root.visibility = View.GONE
        root.addView(errorView, matchParent())
        root.addView(
            progress,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4), Gravity.TOP),
        )
    }

    /** Shows the chat, at [url] or where it was. */
    fun show(url: String?) {
        attach()
        val webView = webView ?: createWebView()
        if (!showing) {
            showing = true
            val title = activity.findViewById<TextView>(views.toolbarTitle)
            savedTitle = title?.text
            title?.text = BaconReaderStrings.chatEntry
            val flipper = activity.findViewById<ViewFlipper>(views.toolbarFlipper)
            if (flipper != null) {
                savedFlipperChild = flipper.displayedChild
                flipper.displayedChild = 0
            }
            // The keyboard must not cover the chat's message box.
            savedSoftInputMode = activity.window.attributes.softInputMode
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            (activity as? ToolbarActivity)?.showActionBar(true)
            activity.findViewById<AppBarLayout>(views.appBar)?.setExpanded(true, false)
            placeBelowToolbar()
            root.visibility = View.VISIBLE
            webView.onResume()
            activity.invalidateOptionsMenu()
        }
        if (url != null) {
            webView.loadUrl(url)
        } else if (webView.url.isNullOrEmpty()) {
            webView.loadUrl(CHAT_URL)
        }
    }

    /** Back to the post list. [restoreTitle] is false when a new feed sets its own title. */
    fun hide(restoreTitle: Boolean) {
        if (!showing) return
        showing = false
        root.visibility = View.GONE
        webView?.onPause()
        CookieManager.getInstance().flush()
        if (restoreTitle) {
            activity.findViewById<TextView>(views.toolbarTitle)?.text = savedTitle
            activity.findViewById<ViewFlipper>(views.toolbarFlipper)?.displayedChild =
                savedFlipperChild
        }
        savedSoftInputMode?.let { activity.window.setSoftInputMode(it) }
        savedSoftInputMode = null
        activity.invalidateOptionsMenu()
    }

    /** Back: the chat's previous page, then the post list. False when it isn't showing. */
    fun back(finishInstead: Boolean): Boolean {
        if (!showing) return false
        val webView = webView
        if (webView != null && errorView.visibility != View.VISIBLE && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        if (finishInstead) activity.finish() else hide(restoreTitle = true)
        return true
    }

    /** Starts the chat over in a new web view, such as for another account's login. */
    fun restart() {
        destroyWebView()
        if (showing) createWebView().loadUrl(CHAT_URL)
    }

    fun reload() {
        val webView = webView
        if (webView == null) {
            show(CHAT_URL)
            return
        }
        errorView.visibility = View.GONE
        val url = webView.url
        if (url.isNullOrBlank() || url == "about:blank") webView.loadUrl(CHAT_URL)
        else webView.reload()
    }

    /** While the chat shows, only the toolbar's refresh stays, and reloads the chat. */
    fun prepareMenu(menu: Menu) {
        if (!showing) return
        for (index in 0 until menu.size()) {
            val item = menu.getItem(index)
            item.isVisible = item.itemId == views.refreshItem
        }
    }

    fun onPause() {
        webView?.onPause()
        // Keeps the login when Android closes the app in the background.
        CookieManager.getInstance().flush()
    }

    fun onResume() {
        if (showing) webView?.onResume()
    }

    fun onActivityResult(resultCode: Int, data: Intent?) {
        val callback = pendingFiles ?: return
        pendingFiles = null
        callback.onReceiveValue(chosenFiles(resultCode, data))
    }

    fun destroy() {
        pendingFiles?.onReceiveValue(null)
        pendingFiles = null
        destroyWebView()
        (root.parent as? ViewGroup)?.removeView(root)
    }

    private fun attach() {
        if (attached) return
        val list = activity.findViewById<ViewGroup>(views.listLayout)
        list.addView(
            root,
            RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                .apply { addRule(RelativeLayout.ABOVE, views.adContainer) },
        )
        attached = true
    }

    private fun placeBelowToolbar() {
        val toolbar = activity.findViewById<View>(views.toolbar)
        val height = toolbar?.height?.takeIf { it > 0 } ?: dp(56)
        val params = root.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (params.topMargin != height) {
            params.topMargin = height
            root.layoutParams = params
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val webView = WebView(activity)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        // The app targets Android 12, where force dark, not algorithmic darkening, is what applies.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            webView.settings.forceDark =
                if (isDark()) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
        }
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        // The chat page talks to matrix.redditspace.com, a third party for www.reddit.com.
        cookies.setAcceptThirdPartyCookies(webView, true)
        webView.webViewClient = ChatClient()
        webView.webChromeClient = ChatChromeClient()
        webView.setDownloadListener { url, _, _, _, _ -> openOutside(Uri.parse(url)) }
        root.addView(webView, 0, matchParent())
        this.webView = webView
        if (!showing) webView.onPause()
        return webView
    }

    private fun destroyWebView() {
        webView?.let {
            root.removeView(it)
            it.destroy()
        }
        webView = null
    }

    private inner class ChatClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?,
        ): Boolean {
            if (request == null || !request.isForMainFrame) return false
            return navigate(view, request.url, request.hasGesture())
        }

        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            if (url == null || Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) return false
            return navigate(view, Uri.parse(url), true)
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            failed = false
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            progress.visibility = View.GONE
            if (!failed) errorView.visibility = View.GONE
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?,
        ) {
            if (request?.isForMainFrame != true) return
            showError(error?.description?.toString())
        }

        @Deprecated("Deprecated in Java")
        override fun onReceivedError(
            view: WebView?,
            errorCode: Int,
            description: String?,
            failingUrl: String?,
        ) {
            // Android 6 and later call the other overload.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) showError(description)
        }

        override fun onRenderProcessGone(
            view: WebView?,
            detail: RenderProcessGoneDetail?,
        ): Boolean {
            // Unhandled, a crash of the WebView's renderer closes the whole app. Retry makes a new
            // WebView.
            Log.w(TAG, "The chat's WebView renderer stopped")
            destroyWebView()
            showError(null)
            return true
        }
    }

    private inner class ChatChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            progress.progress = newProgress
            progress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
        }

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?,
        ): Boolean {
            pendingFiles?.onReceiveValue(null)
            pendingFiles = filePathCallback
            val intent =
                try {
                    fileChooserParams?.createIntent()
                } catch (exception: Exception) {
                    null
                }
                    ?: Intent(Intent.ACTION_GET_CONTENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("*/*")
            if (fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            return try {
                @Suppress("DEPRECATION") activity.startActivityForResult(intent, REQUEST_FILES)
                true
            } catch (exception: ActivityNotFoundException) {
                Log.i(TAG, "No app to choose files with", exception)
                pendingFiles = null
                false
            }
        }
    }

    /**
     * Keeps the chat and Reddit's login pages in the WebView. Other Reddit links open in
     * BaconReader, and other sites in the browser.
     */
    private fun navigate(view: WebView?, uri: Uri?, hasGesture: Boolean): Boolean {
        if (uri == null) return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme == "about" || scheme == "data" || scheme == "blob" || scheme == "javascript") {
            return false
        }
        if (scheme != "http" && scheme != "https") {
            openOutside(uri)
            return true
        }
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        if (isChatLink(uri) || host in SIGN_IN_HOSTS) return false
        if (host == "reddit.com" || host.endsWith(".reddit.com")) {
            val path = uri.path.orEmpty()
            if (LOGIN_PATHS.any { path == it || path.startsWith("$it/") }) return false
            if (!hasGesture) {
                // A redirect, such as to the front page after logging in: back to the chat.
                if (path.isEmpty() || path == "/") {
                    view?.loadUrl(CHAT_URL)
                    return true
                }
                return false
            }
            openInApp(uri)
            return true
        }
        openOutside(uri)
        return true
    }

    private fun openInApp(uri: Uri) {
        try {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, uri).setClassName(activity, LINK_DISPATCHER)
            )
        } catch (exception: Exception) {
            Log.i(TAG, "BaconReader could not open $uri", exception)
            openOutside(uri)
        }
    }

    private fun openOutside(uri: Uri) {
        try {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, uri)
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (exception: ActivityNotFoundException) {
            Log.i(TAG, "No app opens $uri", exception)
        }
    }

    private fun chosenFiles(resultCode: Int, data: Intent?): Array<Uri>? {
        if (resultCode != Activity.RESULT_OK || data == null) return null
        val clip = data.clipData
        if (clip != null && clip.itemCount > 0) {
            return Array(clip.itemCount) { clip.getItemAt(it).uri }
        }
        return WebChromeClient.FileChooserParams.parseResult(resultCode, data)
    }

    private fun showError(description: String?) {
        failed = true
        progress.visibility = View.GONE
        errorText.text =
            if (description.isNullOrBlank()) BaconReaderStrings.chatLoadError
            else BaconReaderStrings.chatLoadError + "\n\n" + description
        errorView.visibility = View.VISIBLE
    }

    private fun retry() {
        errorView.visibility = View.GONE
        if (webView == null) {
            createWebView().loadUrl(CHAT_URL)
            return
        }
        reload()
    }

    private fun errorView(): LinearLayout {
        errorText.gravity = Gravity.CENTER
        errorText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        val button =
            Button(activity).apply {
                text = BaconReaderStrings.chatRetry
                setOnClickListener { retry() }
            }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            setBackgroundColor(background())
            visibility = View.GONE
            // Keeps taps from reaching the WebView's error page underneath.
            isClickable = true
            addView(errorText)
            addView(
                button,
                LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                    .apply { topMargin = dp(16) },
            )
        }
    }

    /** The background of BaconReader's theme. */
    private fun background(): Int {
        val value = TypedValue()
        val found = activity.theme.resolveAttribute(android.R.attr.colorBackground, value, true)
        return if (found && value.type >= TypedValue.TYPE_FIRST_COLOR_INT) value.data
        else if (isSystemNight()) Color.BLACK else Color.WHITE
    }

    /** Whether BaconReader's theme is a dark one, which the chat follows. */
    private fun isDark(): Boolean {
        val color = background()
        val luminance =
            (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) /
                255
        return luminance < 0.5
    }

    private fun isSystemNight(): Boolean =
        activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    private fun matchParent() =
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )

    companion object {
        private const val TAG = "ChatPanel"
        const val REQUEST_FILES = 0x4348
        private const val LINK_DISPATCHER = "com.onelouder.baconreader.LinkDispatcher"

        // Sign-in pages Reddit's login opens.
        private val SIGN_IN_HOSTS =
            setOf("accounts.reddit.com", "accounts.google.com", "appleid.apple.com")
        private val LOGIN_PATHS =
            listOf("/login", "/account", "/register", "/password", "/verification")
    }
}
