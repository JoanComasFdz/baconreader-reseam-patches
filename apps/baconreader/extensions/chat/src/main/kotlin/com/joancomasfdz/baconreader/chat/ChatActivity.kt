// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.chat

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
import android.widget.TextView
import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.common.ErrorDialog
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import java.util.Locale

/**
 * Reddit's mobile website chat, www.reddit.com/chat, in a WebView. Users log in to Reddit's website
 * here once; its cookies stay in the WebView's cookie store.
 */
@Export
class ChatActivity : Activity() {
    private var webView: WebView? = null
    private lateinit var root: FrameLayout
    private lateinit var progress: ProgressBar
    private lateinit var errorView: LinearLayout
    private lateinit var errorText: TextView
    private var pendingFiles: ValueCallback<Array<Uri>>? = null
    private var failed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(
            if (isNight()) android.R.style.Theme_DeviceDefault_NoActionBar
            else android.R.style.Theme_DeviceDefault_Light_NoActionBar
        )
        super.onCreate(savedInstanceState)
        try {
            root = FrameLayout(this)
            progress =
                ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    visibility = View.GONE
                }
            errorView = errorView()
            setContentView(root)
            val webView = createWebView()
            this.webView = webView
            root.addView(webView, matchParent())
            root.addView(
                progress,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4), Gravity.TOP),
            )
            root.addView(errorView, matchParent())
            if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
                webView.loadUrl(startUrl(intent))
            }
        } catch (exception: Exception) {
            // Without a WebView, such as while Android updates it, there is no chat to show.
            ErrorDialog.report(TAG, "Could not open the chat", exception)
            finish()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val url = intent?.data ?: return
        if (isChatLink(url)) webView?.loadUrl(url.toString())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView?.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        webView?.onPause()
        // Keeps the login when Android closes the app in the background.
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onDestroy() {
        pendingFiles?.onReceiveValue(null)
        pendingFiles = null
        webView?.let {
            root.removeView(it)
            it.destroy()
        }
        webView = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val webView = webView
        if (webView != null && errorView.visibility != View.VISIBLE && webView.canGoBack()) {
            webView.goBack()
            return
        }
        @Suppress("DEPRECATION") super.onBackPressed()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_FILES) {
            @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
            return
        }
        val callback = pendingFiles ?: return
        pendingFiles = null
        callback.onReceiveValue(chosenFiles(resultCode, data))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val webView = WebView(this)
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
                if (isNight()) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
        }
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        // The chat page talks to matrix.redditspace.com, a third party for www.reddit.com.
        cookies.setAcceptThirdPartyCookies(webView, true)
        webView.webViewClient = ChatClient()
        webView.webChromeClient = ChatChromeClient()
        webView.setDownloadListener { url, _, _, _, _ -> openOutside(Uri.parse(url)) }
        return webView
    }

    private fun startUrl(intent: Intent?): String {
        val data = intent?.data
        return if (data != null && isChatLink(data)) data.toString() else CHAT_URL
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
            // Unhandled, a crash of the WebView's renderer closes the whole app.
            Log.w(TAG, "The chat's WebView renderer stopped")
            webView?.let {
                root.removeView(it)
                it.destroy()
            }
            webView = null
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
                @Suppress("DEPRECATION") startActivityForResult(intent, REQUEST_FILES)
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
            startActivity(Intent(Intent.ACTION_VIEW, uri).setClassName(this, LINK_DISPATCHER))
        } catch (exception: Exception) {
            Log.i(TAG, "BaconReader could not open $uri", exception)
            openOutside(uri)
        }
    }

    private fun openOutside(uri: Uri) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, uri)
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (exception: ActivityNotFoundException) {
            Log.i(TAG, "No app opens $uri", exception)
        }
    }

    private fun chosenFiles(resultCode: Int, data: Intent?): Array<Uri>? {
        if (resultCode != RESULT_OK || data == null) return null
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
        val webView = webView
        if (webView == null) {
            recreate()
            return
        }
        val url = webView.url
        if (url.isNullOrBlank() || url == "about:blank") webView.loadUrl(CHAT_URL)
        else webView.reload()
    }

    private fun errorView(): LinearLayout {
        val background = TypedValue()
        theme.resolveAttribute(android.R.attr.colorBackground, background, true)
        errorText =
            TextView(this).apply {
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            }
        val button =
            Button(this).apply {
                text = BaconReaderStrings.chatRetry
                setOnClickListener { retry() }
            }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            setBackgroundColor(background.data)
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

    private fun isNight(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun matchParent() =
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )

    private companion object {
        const val TAG = "ChatActivity"
        const val REQUEST_FILES = 0x4348
        const val LINK_DISPATCHER = "com.onelouder.baconreader.LinkDispatcher"

        // Sign-in pages Reddit's login opens.
        val SIGN_IN_HOSTS = setOf("accounts.reddit.com", "accounts.google.com", "appleid.apple.com")
        val LOGIN_PATHS = listOf("/login", "/account", "/register", "/password", "/verification")
    }
}
