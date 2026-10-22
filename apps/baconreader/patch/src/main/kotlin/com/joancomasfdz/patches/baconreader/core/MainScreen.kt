// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.core

import app.reseam.patch.klass
import app.reseam.patch.method

/** The main screen coming back to the front, MainActivity.onResume. */
val mainActivityResume = klass("com.onelouder.baconreader.ui.main.MainActivity").method("onResume")
