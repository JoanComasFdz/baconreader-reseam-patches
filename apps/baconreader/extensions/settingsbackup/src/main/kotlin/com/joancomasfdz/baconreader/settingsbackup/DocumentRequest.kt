// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:Suppress("DEPRECATION")

package com.joancomasfdz.baconreader.settingsbackup

import android.app.Activity
import android.app.Fragment
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/**
 * Asks Android for a file to write or read, and hands it to [SettingsBackupFiles]. The patches'
 * settings screen isn't BaconReader's, so its result methods can't be hooked: a fragment without a
 * view gets the result instead, even after Android recreated the screen.
 */
class DocumentRequest : Fragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val intent = arguments?.getParcelable<Intent>(ARGUMENT_INTENT) ?: return done()
        startActivityForResult(intent, REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_CODE) return
        val activity = activity
        val uri: Uri? = data?.data
        val export = arguments?.getBoolean(ARGUMENT_EXPORT) == true
        done()
        if (activity == null || resultCode != Activity.RESULT_OK || uri == null) return
        if (export) SettingsBackupFiles.export(activity, uri)
        else SettingsBackupFiles.import(activity, uri)
    }

    private fun done() {
        fragmentManager?.beginTransaction()?.remove(this)?.commitAllowingStateLoss()
    }

    companion object {
        private const val TAG = "baconreader_patches_settings_backup"
        private const val REQUEST_CODE = 0x5E77
        private const val ARGUMENT_INTENT = "intent"
        private const val ARGUMENT_EXPORT = "export"

        fun start(activity: Activity, intent: Intent, export: Boolean) {
            val request =
                DocumentRequest().apply {
                    arguments =
                        Bundle().apply {
                            putParcelable(ARGUMENT_INTENT, intent)
                            putBoolean(ARGUMENT_EXPORT, export)
                        }
                }
            activity.fragmentManager.beginTransaction().add(request, TAG).commitAllowingStateLoss()
        }
    }
}
