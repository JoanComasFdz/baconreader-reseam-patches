// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.media

import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.point
import app.reseam.patch.redirectTo
import com.joancomasfdz.baconreader.redgifs.bindings.RedgifsFix
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM

private const val REDGIFS_MANAGER = "com.onelouder.baconreader.media.gfycat.RedGifsManager"

// Redgifs dropped the OAuth client flow BaconReader's own interceptor authenticates with, so
// every Redgifs video fails to load. Redgifs now hands out temporary tokens instead.
val fixRedgifsApi =
    patch("Fix Redgifs API") {
        description("Makes Redgifs videos play again.")
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)

        execute { headerInterceptor.redirectTo(RedgifsFix.replaceInterceptor) }
    }

// Redirecting the call rather than the builder's build() covers both of the method's build paths,
// including the one taken when setting up SSL fails.
private val headerInterceptor =
    klass(REDGIFS_MANAGER)
        .method("getOkhttpClient")
        .point { newInstance("$REDGIFS_MANAGER\$HeaderInterceptor") }
        .next {
            invokeVirtual {
                owner("okhttp3.OkHttpClient\$Builder")
                name("addInterceptor")
            }
        }
