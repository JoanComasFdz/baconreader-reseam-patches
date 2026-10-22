// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

@file:JvmName("RedgifsFix")

package com.joancomasfdz.baconreader.redgifs

import app.reseam.runtime.Export
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/** Adds [RedgifsInterceptor] where BaconReader adds its own Redgifs interceptor, which it drops. */
@Export
@Suppress("UNUSED_PARAMETER")
fun replaceInterceptor(
    builder: OkHttpClient.Builder,
    original: Interceptor?,
): OkHttpClient.Builder = builder.addInterceptor(RedgifsInterceptor)
