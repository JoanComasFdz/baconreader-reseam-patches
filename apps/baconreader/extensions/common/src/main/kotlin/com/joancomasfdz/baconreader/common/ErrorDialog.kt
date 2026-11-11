// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.common

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shows the errors of the patches in a dialog with all the details, instead of only logging them:
 * what failed, the patch whose code is in the stack trace, the error and its causes, the time, the
 * app, patches and Android versions, the heap in use, and the stack trace. The details can be
 * copied or saved to a file, to report them.
 */
object ErrorDialog {
    private const val TAG = "ErrorDialog"

    // A repeating error must not grow the open dialog forever.
    private const val MAX_ERRORS = 20
    private const val SEPARATOR = "\n\n────────────\n\n"
    private const val PATCHES_PACKAGE = "com.joancomasfdz."
    private const val CRASH_FILE = "patches_last_crash.txt"
    private const val EXTENSIONS = "com.joancomasfdz.baconreader."
    private const val MEGABYTE = 1024L * 1024L
    private const val ISSUES = "https://github.com/JoanComasFdz/baconreader-reseam-patches/issues"

    // The patch each extension's code belongs to, by its package under EXTENSIONS. The shared
    // extensions (common, settings) belong to no patch.
    private val EXTENSION_PATCHES: Map<String, () -> String> =
        mapOf(
            "accountcolors" to { BaconReaderStrings.patchAccountColors },
            "sidebar" to { BaconReaderStrings.patchDisableUnavailableSidebar },
            "embedcommentimages" to { BaconReaderStrings.patchEmbedCommentImages },
            "savedfiles" to { BaconReaderStrings.patchFixEmptySavedFiles },
            "imgur" to { BaconReaderStrings.patchFixImgurApi },
            "thumbnails" to { BaconReaderStrings.patchFixListThumbnails },
            "votes" to { BaconReaderStrings.patchFixLostPostVotes },
            "memes" to { BaconReaderStrings.patchFixMemeTemplates },
            "redditlinks" to { BaconReaderStrings.patchFixRedditLinks },
            "redgifs" to { BaconReaderStrings.patchFixRedgifsApi },
            "slideshow" to { BaconReaderStrings.patchFixSlideShow },
            "unread" to { BaconReaderStrings.patchFixUnreadMessages },
            "flairs" to { BaconReaderStrings.patchFlairFilter },
            "postmedia" to { BaconReaderStrings.patchImprovePostMediaLoading },
            "inboxreply" to { BaconReaderStrings.patchInboxCommentReplyScreen },
            "editor" to { BaconReaderStrings.patchMarkdownEditor },
            "settingsentry" to { BaconReaderStrings.patchPatchesEntryInSettings },
            "openlinks" to { BaconReaderStrings.patchPromptToOpenRedditLinks },
            "chat" to { BaconReaderStrings.patchRedditChat },
            "awards" to { BaconReaderStrings.patchRemoveAwards },
            "videosound" to { BaconReaderStrings.patchSaveVideosWithSound },
            "search" to { BaconReaderStrings.patchSearchComments },
            "selecttext" to { BaconReaderStrings.patchSelectText },
            "tables" to { BaconReaderStrings.patchShowTables },
            "avatars" to { BaconReaderStrings.patchShowUserAvatars },
            "textposticon" to { BaconReaderStrings.patchTextPostIcon },
            "detailvideo" to { BaconReaderStrings.patchVideoAutoplayInDetailView },
        )

    // The comments extension serves three patches, so its classes name theirs.
    private val CLASS_PATCHES: Map<String, () -> String> =
        mapOf(
            "comments.LoadMoreComments" to { BaconReaderStrings.patchLoadMoreComments },
            "comments.MoreChildrenFallback" to { BaconReaderStrings.patchFixLoadMoreComments },
            "comments.HiddenSpoilers" to { BaconReaderStrings.patchHideFormattedSpoilers },
        )

    private val main = Handler(Looper.getMainLooper())

    @Volatile private var application: Application? = null

    // The rest is only used on the main thread.
    private var resumed = WeakReference<Activity>(null)

    // Errors that happened while no screen was showing, shown when one is.
    private val pending = StringBuilder()
    private var pendingCount = 0

    private var dialog: Dialog? = null
    private var dialogActivity = WeakReference<Activity>(null)
    private var detailsView: TextView? = null
    private var dialogCount = 0

    /** Follows the screen on top to show the dialog on. The settings host calls it at app start. */
    @Export
    @JvmStatic
    fun init(application: Application?) {
        if (application == null || this.application != null) return
        this.application = application
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    resumed = WeakReference(activity)
                    if (pendingCount == 0) return
                    val details = pending.toString()
                    val count = pendingCount
                    pending.setLength(0)
                    pendingCount = 0
                    show(activity, details, count)
                }

                override fun onActivityPaused(activity: Activity) {
                    if (resumed.get() === activity) resumed = WeakReference(null)
                }

                override fun onActivityDestroyed(activity: Activity) {
                    if (dialogActivity.get() === activity) forgetDialog()
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

                override fun onActivityStarted(activity: Activity) {}

                override fun onActivityStopped(activity: Activity) {}

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            }
        )
        catchErrors(application)
    }

    // A patch's error is caught wherever it escapes to, and shown here instead of closing the app.
    // Any other crash still closes it, as it would without the patches, but its details are kept
    // and shown when the app opens again.
    private fun catchErrors(application: Application) {
        showLastCrash(application)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (thread !== Looper.getMainLooper().thread && fromPatches(error)) {
                report("Patches", "A patch failed on the thread ${thread.name}", error)
            } else {
                saveCrash(application, thread, error)
                previous?.uncaughtException(thread, error)
            }
        }
        // An error on the main thread ends its message loop. Running the loop again inside a
        // message lets a patch's error be caught there and the loop continue.
        main.post(::loopMainThread)
    }

    private fun loopMainThread() {
        while (true) {
            try {
                Looper.loop()
                return
            } catch (error: Throwable) {
                if (!fromPatches(error)) throw error
                report("Patches", "A patch failed", error)
            }
        }
    }

    private fun fromPatches(error: Throwable): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause.stackTrace.any { it.className.startsWith(PATCHES_PACKAGE) }) return true
            cause = cause.cause.takeIf { it !== cause }
        }
        return false
    }

    private fun saveCrash(application: Application, thread: Thread, error: Throwable) {
        try {
            val details =
                details(
                    "BaconReader",
                    "BaconReader closed because of this error on the thread ${thread.name}",
                    error,
                )
            File(application.filesDir, CRASH_FILE).writeText(details)
        } catch (ignored: Throwable) {
            // The crash is in the log either way.
        }
    }

    private fun showLastCrash(application: Application) {
        val file = File(application.filesDir, CRASH_FILE)
        if (!file.exists()) return
        try {
            show(file.readText())
        } catch (exception: IOException) {
            Log.e(TAG, "Could not read the last crash", exception)
        } finally {
            file.delete()
        }
    }

    /**
     * Logs the error and shows it in the dialog, over the screen on top. More errors while the
     * dialog is open are added to it, and errors while the app is in the background are shown when
     * it comes back. [tag] names what failed, such as the extension's log tag. Callable from any
     * thread.
     */
    fun report(tag: String, message: String, error: Throwable? = null) {
        Log.e(tag, message, error)
        show(details(tag, message, error))
    }

    private fun show(details: String) {
        main.post {
            val activity = resumed.get()
            if (activity == null || activity.isFinishing) {
                if (pendingCount < MAX_ERRORS) {
                    if (pending.isNotEmpty()) pending.append(SEPARATOR)
                    pending.append(details)
                    pendingCount++
                }
            } else {
                show(activity, details, 1)
            }
        }
    }

    private fun details(tag: String, message: String, error: Throwable?): String = buildString {
        append(tag).append(": ").append(message).append('\n')
        append(failedPatch(error)).append('\n')
        var cause = error
        while (cause != null) {
            append(if (cause === error) "\nError: " else "Caused by: ").append(cause.javaClass.name)
            cause.message?.let { append(": ").append(it) }
            append('\n')
            cause = cause.cause.takeIf { it !== cause }
        }

        append("\nTime: ")
        append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())).append('\n')
        application?.let { app ->
            append("App: ").append(app.packageName).append(' ').append(appVersion(app)).append('\n')
            append("Patches: ").append(BaconReaderPatches.version(app)).append('\n')
        }
        append("Android: ").append(Build.VERSION.RELEASE).append(" (API ")
        append(Build.VERSION.SDK_INT).append("), ").append(Build.MANUFACTURER).append(' ')
        append(Build.MODEL).append('\n')
        append(memory()).append('\n')

        append("\nStack trace:\n")
        val stackTrace = StringWriter()
        // Without an error, where it was reported from.
        (error ?: Throwable("Reported at")).printStackTrace(PrintWriter(stackTrace))
        append(stackTrace.toString().trim())
    }

    /** The patches whose code is in the stack trace, or that none of its frames is from. */
    private fun failedPatch(error: Throwable?): String {
        val patches = LinkedHashSet<String>()
        var cause = error
        while (cause != null) {
            for (frame in cause.stackTrace) patchOf(frame.className)?.let { patches.add(it) }
            cause = cause.cause.takeIf { it !== cause }
        }
        return if (patches.isEmpty()) {
            "Failed patch: none of the stack trace's frames is from a patch's code"
        } else {
            "Failed patch: " + patches.joinToString(", ")
        }
    }

    private fun patchOf(className: String): String? {
        if (!className.startsWith(EXTENSIONS)) return null
        val name = className.substringBefore('$').removeSuffix("Kt")
        CLASS_PATCHES[name.removePrefix(EXTENSIONS)]?.let {
            return it()
        }
        val extension = name.removePrefix(EXTENSIONS).substringBefore('.')
        return EXTENSION_PATCHES[extension]?.invoke()
    }

    /** The heap in use against its limit: an out-of-memory error is about how full it was. */
    private fun memory(): String {
        val runtime = Runtime.getRuntime()
        val used = (runtime.totalMemory() - runtime.freeMemory()) / MEGABYTE
        val limit = runtime.maxMemory() / MEGABYTE
        return "Memory: $used MB of the heap used, out of $limit MB"
    }

    @Suppress("DEPRECATION")
    private fun appVersion(app: Application): String =
        try {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName.toString()
        } catch (exception: Exception) {
            "unknown"
        }

    private fun show(activity: Activity, details: String, count: Int) {
        try {
            val open = dialog
            val openDetails = detailsView
            if (
                open != null &&
                    open.isShowing &&
                    dialogActivity.get() === activity &&
                    openDetails != null
            ) {
                if (dialogCount >= MAX_ERRORS) return
                openDetails.append(SEPARATOR + details)
                dialogCount += count
                return
            }

            val ui = BaconReaderPatches.bundle().ui(activity)
            val text =
                ui.text(details, ui.type.bodyMedium, ui.colors.onSurfaceVariant).apply {
                    typeface = Typeface.MONOSPACE
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextIsSelectable(true)
                }
            // Above the details, so that the link shows without scrolling to them.
            val issue =
                ui.text(
                        BaconReaderStrings.errorIssue,
                        ui.type.bodyMedium,
                        ui.colors.onSurfaceVariant,
                    )
                    .apply {
                        paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                        setOnClickListener { openIssues(activity) }
                    }
            val content =
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(issue)
                    addView(text)
                }
            lateinit var created: Dialog
            created =
                ui.dialog()
                    .title(BaconReaderStrings.errorTitle)
                    .view(content)
                    .action(BaconReaderStrings.errorCopy) { copy(activity, text.text.toString()) }
                    .action(BaconReaderStrings.errorSave) { save(activity, text.text.toString()) }
                    .dismissAction(BaconReaderStrings.errorClose)
                    .onDismiss { if (dialog === created) forgetDialog() }
                    .show()
            created.setCanceledOnTouchOutside(false)
            dialog = created
            dialogActivity = WeakReference(activity)
            detailsView = text
            dialogCount = count
        } catch (exception: Exception) {
            // The details are in the log either way.
            Log.e(TAG, "Could not show the error dialog", exception)
            Toast.makeText(activity, details.take(300), Toast.LENGTH_LONG).show()
        }
    }

    private fun forgetDialog() {
        dialog = null
        dialogActivity = WeakReference(null)
        detailsView = null
    }

    /** The repo's issues, where the error can be reported. */
    private fun openIssues(context: Context) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ISSUES)))
        } catch (exception: Exception) {
            Toast.makeText(context, ISSUES, Toast.LENGTH_LONG).show()
        }
    }

    private fun copy(context: Context, details: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("BaconReader error", details))
        Toast.makeText(context, BaconReaderStrings.errorCopied, Toast.LENGTH_SHORT).show()
    }

    /** Saves to Downloads, which needs no permission from Android 10, else to the app's folder. */
    private fun save(context: Context, details: String) {
        val name =
            "baconreader-error-" +
                SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) +
                ".txt"
        val bytes = details.toByteArray(Charsets.UTF_8)
        try {
            val location =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values =
                        ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                            put(
                                MediaStore.MediaColumns.RELATIVE_PATH,
                                Environment.DIRECTORY_DOWNLOADS,
                            )
                        }
                    val uri =
                        resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                            ?: throw IOException("No Downloads folder")
                    (resolver.openOutputStream(uri) ?: throw IOException("Cannot write $uri")).use {
                        it.write(bytes)
                    }
                    Environment.DIRECTORY_DOWNLOADS + "/" + name
                } else {
                    val file = File(context.getExternalFilesDir(null), name)
                    file.writeBytes(bytes)
                    file.absolutePath
                }
            Toast.makeText(context, BaconReaderStrings.errorSaved(location), Toast.LENGTH_LONG)
                .show()
        } catch (exception: Exception) {
            Log.e(TAG, "Could not save the error details", exception)
            Toast.makeText(
                    context,
                    BaconReaderStrings.errorSaveFailed(exception.message.toString()),
                    Toast.LENGTH_LONG,
                )
                .show()
        }
    }
}
