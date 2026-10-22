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
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.TypedValue
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
import org.json.JSONObject

/**
 * Shows the errors of the patches in a dialog with all the details, instead of only logging them:
 * what failed, the error and its causes, the time, the app, patches and Android versions, and the
 * stack trace. The details can be copied or saved to a file, to report them.
 */
object ErrorDialog {
    private const val TAG = "ErrorDialog"

    // A repeating error must not grow the open dialog forever.
    private const val MAX_ERRORS = 20
    private const val SEPARATOR = "\n\n────────────\n\n"

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
    }

    /**
     * Logs the error and shows it in the dialog, over the screen on top. More errors while the
     * dialog is open are added to it, and errors while the app is in the background are shown when
     * it comes back. [tag] names what failed, such as the extension's log tag. Callable from any
     * thread.
     */
    fun report(tag: String, message: String, error: Throwable? = null) {
        Log.e(tag, message, error)
        val details = details(tag, message, error)
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
            append("Patches: ").append(patchesVersion(app)).append('\n')
        }
        append("Android: ").append(Build.VERSION.RELEASE).append(" (API ")
        append(Build.VERSION.SDK_INT).append("), ").append(Build.MANUFACTURER).append(' ')
        append(Build.MODEL).append('\n')

        append("\nStack trace:\n")
        val stackTrace = StringWriter()
        // Without an error, where it was reported from.
        (error ?: Throwable("Reported at")).printStackTrace(PrintWriter(stackTrace))
        append(stackTrace.toString().trim())
    }

    @Suppress("DEPRECATION")
    private fun appVersion(app: Application): String =
        try {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName.toString()
        } catch (exception: Exception) {
            "unknown"
        }

    // The settings host writes the bundle's version, when it has one, and the engine's into the
    // app's assets.
    private fun patchesVersion(app: Application): String =
        try {
            val path = "reseam/${BaconReaderPatches.NAME}/settings.json"
            val json = JSONObject(app.assets.open(path).bufferedReader().use { it.readText() })
            val bundle = json.getJSONObject("bundle")
            val version = if (bundle.isNull("version")) "unknown" else bundle.getString("version")
            "$version (Reseam ${json.optString("engine")})"
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
            lateinit var created: Dialog
            created =
                ui.dialog()
                    .title(BaconReaderStrings.errorTitle)
                    .view(text)
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
