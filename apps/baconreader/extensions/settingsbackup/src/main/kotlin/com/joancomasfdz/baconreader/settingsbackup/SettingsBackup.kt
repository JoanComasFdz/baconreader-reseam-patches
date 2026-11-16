// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("SettingsBackup")

package com.joancomasfdz.baconreader.settingsbackup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import app.reseam.runtime.Export
import com.joancomasfdz.baconreader.common.BaconReaderPatches
import com.joancomasfdz.baconreader.common.ErrorDialog
import com.joancomasfdz.baconreader.settings.BaconReaderStrings
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

private const val TAG = "SettingsBackup"

/** The file's layout. A file with another `format` isn't read. */
private const val FORMAT = 1
private const val KEY_FORMAT = "format"
private const val SECTION_BACONREADER = "baconreader"
private const val SECTION_PATCHES = "patches"
private const val SECTION_ACCOUNT_COLORS = "account_colors"

// A settings file is a few kilobytes; anything much bigger was picked by mistake.
private const val MAX_FILE_BYTES = 1024 * 1024

// For any other type, Android's file providers add the type's extension to the name: ".yaml.txt".
private const val FILE_TYPE = "application/octet-stream"

/** The patches' own file, where Account colors keeps each account's color. */
private const val PATCHES_PREFERENCES = "baconreader_patches"
private const val ACCOUNT_COLOR_PREFIX = "account_colors.color."

/** The file the app runtime keeps the patches' settings in. */
private const val RUNTIME_PREFERENCES = "reseam." + BaconReaderPatches.NAME

/**
 * BaconReader's preferences that aren't settings: its accounts and logins (older versions kept them
 * here, before moving them to the database), and what it keeps about this device and install. They
 * are neither exported nor changed by an import.
 */
private val PRIVATE_KEYS =
    setOf(
        "accounts",
        "username",
        "device_identifier",
        "DataId",
        "firstLaunchTs",
        "LastAppVersion",
        "app_retained",
        "timeSessionTotal",
        "lastNotifiedMessageTime",
        "RedGifsExpireTime",
        "SHOW_REVIEW_TS",
        "reviewVisible",
        "WhatsNewDialogShownNew",
        "KEY_ALREADY_SHOWN",
        // A folder Android gave this install access to, which another one can't use.
        "storage_uri",
        "storage_image",
    )

// The ads' consent strings, kept by their libraries in the same file.
private val PRIVATE_PREFIXES = listOf("IABTCF_", "IABGPP_", "IABUSPrivacy")

/** Called by the "Export settings" row: asks where to save the file. */
@Export
fun exportSettings(activity: Activity?) {
    activity ?: return
    try {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        val intent =
            Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(FILE_TYPE)
                .putExtra(Intent.EXTRA_TITLE, "baconreader-settings-$date.yaml")
        DocumentRequest.start(activity, intent, export = true)
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not export the settings", exception)
    }
}

/** Called by the "Import settings" row: asks for the file. */
@Export
fun importSettings(activity: Activity?) {
    activity ?: return
    try {
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        DocumentRequest.start(activity, intent, export = false)
    } catch (exception: Exception) {
        ErrorDialog.report(TAG, "Could not import the settings", exception)
    }
}

/** What to do with the file Android gave [DocumentRequest]. */
internal object SettingsBackupFiles {
    fun export(activity: Activity, uri: Uri) {
        try {
            val text = export(activity)
            val stream =
                activity.contentResolver.openOutputStream(uri, "wt")
                    ?: throw IOException("The file can't be written")
            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            Toast.makeText(
                    activity,
                    BaconReaderStrings.settingsBackupExported(displayName(activity, uri)),
                    Toast.LENGTH_LONG,
                )
                .show()
        } catch (exception: Exception) {
            Log.e(TAG, "Could not export the settings", exception)
            Toast.makeText(
                    activity,
                    BaconReaderStrings.settingsBackupExportFailed(exception.message.toString()),
                    Toast.LENGTH_LONG,
                )
                .show()
        }
    }

    fun import(activity: Activity, uri: Uri) {
        val name = displayName(activity, uri)
        val file =
            try {
                read(activity, uri)
            } catch (exception: SettingsYamlException) {
                showFailure(
                    activity,
                    BaconReaderStrings.settingsBackupBadLine(
                        exception.line.toString(),
                        exception.message.toString(),
                    ),
                )
                return
            } catch (exception: Exception) {
                ErrorDialog.report(TAG, "Could not read $name", exception)
                return
            }
        if (file == null) {
            showFailure(activity, BaconReaderStrings.settingsBackupNotABackup)
            return
        }
        val count = file.values.sumOf { it.size }
        BaconReaderPatches.bundle()
            .ui(activity)
            .dialog()
            .title(BaconReaderStrings.settingsBackupImportTitle)
            .message(BaconReaderStrings.settingsBackupImportMessage(name, count.toString()))
            .action(BaconReaderStrings.settingsBackupImport) {
                try {
                    apply(activity, file)
                    restart(activity)
                } catch (exception: Exception) {
                    ErrorDialog.report(TAG, "Could not import the settings", exception)
                }
            }
            .dismissAction(BaconReaderStrings.settingsBackupCancel)
            .show()
    }
}

/** Opens BaconReader again in a new process, which reads the imported settings. */
private fun restart(activity: Activity) {
    val launch = activity.packageManager.getLaunchIntentForPackage(activity.packageName) ?: return
    activity.startActivity(Intent.makeRestartActivityTask(launch.component))
    Runtime.getRuntime().exit(0)
}

private fun showFailure(activity: Activity, message: String) {
    BaconReaderPatches.bundle()
        .ui(activity)
        .dialog()
        .title(BaconReaderStrings.settingsBackupImportFailed)
        .message(message)
        .dismissAction(BaconReaderStrings.settingsBackupClose)
        .show()
}

private fun export(context: Context): String {
    val baconReader = sortedMapOf<String, Any>(String.CASE_INSENSITIVE_ORDER)
    appPreferences(context).all.forEach { (key, value) ->
        if (value != null && isExported(key)) baconReader[key] = value
    }

    val runtime = BaconReaderPatches.bundle().settings
    val patches = LinkedHashMap<String, Any>()
    patchSettings(context).forEach { setting ->
        patches[setting.name] =
            when (setting.type) {
                "toggle" -> runtime.getBoolean(setting.key, setting.default as Boolean)
                "choice" -> runtime.getChoice(setting.key)
                else -> runtime.getString(setting.key, setting.default.toString())
            }
    }

    val accountColors = sortedMapOf<String, Any>()
    patchesPreferences(context).all.forEach { (key, value) ->
        if (key.startsWith(ACCOUNT_COLOR_PREFIX) && value is Int) {
            accountColors[key.removePrefix(ACCOUNT_COLOR_PREFIX)] = value
        }
    }

    val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date())
    return SettingsYaml.write(
        listOf(
            "BaconReader settings, exported on $date by the BaconReader patches " +
                BaconReaderPatches.version(context) +
                ".",
            "Import them in BaconReader: Settings > Patches > Backup > Import settings.",
            "Accounts, logins and what BaconReader keeps about this device are not in this file.",
        ),
        mapOf(KEY_FORMAT to FORMAT),
        linkedMapOf(
            SECTION_BACONREADER to baconReader,
            SECTION_PATCHES to patches,
            SECTION_ACCOUNT_COLORS to accountColors,
        ),
    )
}

/** The file's sections, or null when it isn't a settings file. */
private fun read(context: Context, uri: Uri): Map<String, Map<String, Any>>? {
    val stream =
        context.contentResolver.openInputStream(uri) ?: throw IOException("The file can't be read")
    val bytes = stream.use { it.readBytes() }
    if (bytes.size > MAX_FILE_BYTES) return null
    val (scalars, maps) = SettingsYaml.read(String(bytes, Charsets.UTF_8))
    if (scalars[KEY_FORMAT] != FORMAT) return null
    return maps.filterKeys {
        it == SECTION_BACONREADER || it == SECTION_PATCHES || it == SECTION_ACCOUNT_COLORS
    }
}

/**
 * Replaces each section the file has. A setting it doesn't list goes back to its default, so the
 * result is the exported settings, whatever was set before.
 */
private fun apply(context: Context, file: Map<String, Map<String, Any>>) {
    file[SECTION_BACONREADER]?.let { values ->
        val preferences = appPreferences(context)
        val current = preferences.all
        val editor = preferences.edit()
        current.keys.filter { isExported(it) && it !in values }.forEach { editor.remove(it) }
        values.forEach { (key, value) ->
            if (!isExported(key)) return@forEach
            when (val converted = convert(value, current[key])) {
                null -> Log.w(TAG, "Skipped $key: $value doesn't fit ${current[key]}")
                else -> put(editor, key, converted)
            }
        }
        editor.commit()
    }

    file[SECTION_PATCHES]?.let { values ->
        val runtime = BaconReaderPatches.bundle().settings
        patchSettings(context).forEach { setting ->
            val value = values[setting.name]
            when (setting.type) {
                "toggle" ->
                    runtime.setBoolean(
                        setting.key,
                        convert(value ?: setting.default, false) as? Boolean
                            ?: setting.default as Boolean,
                    )
                "choice" -> {
                    val choice = value?.toString()
                    runtime.setString(
                        setting.key,
                        if (choice in setting.choices) choice!! else setting.default.toString(),
                    )
                }
                else -> runtime.setString(setting.key, (value ?: setting.default).toString())
            }
        }
        // The runtime saves in the background, and the restart doesn't wait for it.
        context.getSharedPreferences(RUNTIME_PREFERENCES, Context.MODE_PRIVATE).edit().commit()
    }

    file[SECTION_ACCOUNT_COLORS]?.let { values ->
        val preferences = patchesPreferences(context)
        val editor = preferences.edit()
        preferences.all.keys
            .filter { it.startsWith(ACCOUNT_COLOR_PREFIX) }
            .forEach {
                editor.remove(it)
            }
        values.forEach { (name, value) ->
            (convert(value, 0) as? Int)?.let {
                editor.putInt(ACCOUNT_COLOR_PREFIX + name.lowercase(Locale.ROOT), it)
            }
        }
        editor.commit()
    }
}

private fun isExported(key: String): Boolean =
    key !in PRIVATE_KEYS &&
        PRIVATE_PREFIXES.none { key.startsWith(it) } &&
        !key.contains("token", ignoreCase = true)

/** [value] as the type of [like], the value the key has now; null when it can't be. */
private fun convert(value: Any, like: Any?): Any? =
    when (like) {
        null -> value
        is Boolean ->
            when (value) {
                is Boolean -> value
                is String -> value.lowercase(Locale.ROOT).toBooleanStrictOrNull()
                else -> null
            }
        is Int ->
            when (value) {
                is Int -> value
                is Long -> value.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
                is String -> value.trim().toIntOrNull()
                else -> null
            }
        is Long ->
            when (value) {
                is Int -> value.toLong()
                is Long -> value
                is String -> value.trim().toLongOrNull()
                else -> null
            }
        is Float ->
            when (value) {
                is Float -> value
                is Int -> value.toFloat()
                is Long -> value.toFloat()
                is String -> value.trim().toFloatOrNull()
                else -> null
            }
        is String -> if (value is Set<*>) null else value.toString()
        is Set<*> -> value as? Set<*>
        else -> null
    }

private fun put(editor: SharedPreferences.Editor, key: String, value: Any) {
    when (value) {
        is Boolean -> editor.putBoolean(key, value)
        is Int -> editor.putInt(key, value)
        is Long -> editor.putLong(key, value)
        is Float -> editor.putFloat(key, value)
        is Set<*> -> editor.putStringSet(key, value.map { it.toString() }.toSet())
        else -> editor.putString(key, value.toString())
    }
}

/** A setting of the patches, as the settings host lists it for the patches that were applied. */
private class PatchSetting(
    val key: String,
    val type: String,
    val default: Any,
    val choices: List<String>,
) {
    /** The name in the file, the same for both patchers: the key without its group. */
    val name: String = key.substringAfter('.')
}

/**
 * The patches' toggles, texts and choices. Folders belong to this install, and actions aren't
 * values.
 */
private fun patchSettings(context: Context): List<PatchSetting> {
    val path = "reseam/${BaconReaderPatches.NAME}/settings.json"
    val json = JSONObject(context.assets.open(path).bufferedReader().use { it.readText() })
    val settings = ArrayList<PatchSetting>()
    val sections = json.getJSONArray("sections")
    for (i in 0 until sections.length()) {
        val list = sections.getJSONObject(i).getJSONArray("settings")
        for (j in 0 until list.length()) {
            val setting = list.getJSONObject(j)
            val type = setting.getString("type")
            if (type != "toggle" && type != "text" && type != "choice") continue
            val choices = ArrayList<String>()
            setting.optJSONArray("choices")?.let {
                for (k in 0 until it.length()) choices.add(it.getJSONObject(k).getString("value"))
            }
            settings.add(
                PatchSetting(setting.getString("key"), type, setting.get("default"), choices)
            )
        }
    }
    return settings
}

private fun appPreferences(context: Context): SharedPreferences =
    context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)

private fun patchesPreferences(context: Context): SharedPreferences =
    context.getSharedPreferences(PATCHES_PREFERENCES, Context.MODE_PRIVATE)

private fun displayName(context: Context, uri: Uri): String =
    try {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (exception: Exception) {
        null
    } ?: uri.lastPathSegment ?: uri.toString()
