// SPDX-FileCopyrightText: 2026 ReVanced contributors
// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.misc

import app.reseam.patch.dex.Opcode
import app.reseam.patch.dex.buildInstructions
import app.reseam.patch.dex.regA
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import app.reseam.patch.point
import com.joancomasfdz.patches.baconreader.core.BACONREADER
import com.joancomasfdz.patches.baconreader.core.BACONREADER_PREMIUM

private const val IO_EXCEPTION = "java.io.IOException"

// Every Reddit request catches its IOException and throws a new one with the message saying that
// BaconReader stopped working in July 2023. The app's own errors are useful ("reddit: 404 Not
// Found", timeouts, parse errors), so the handler throws the caught error again instead.
val showRequestErrors =
    patch("Show request errors") {
        description(
            "Shows the actual error when a Reddit request fails, instead of the message saying that BaconReader stopped working in July 2023."
        )
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)

        execute {
            // The handler is new-instance, const-string, invoke-direct and throw. The engine has no
            // edit that removes a catch block, so it becomes move-exception and throw.
            val method = farewellThrow.method.method
            val throwIndex = farewellThrow.index
            val exception =
                farewellThrow.instruction.regA ?: error("The farewell throw has no register")
            method.removeInstructions(throwIndex - 2, 2)
            method.replaceInstruction(
                throwIndex - 3,
                buildInstructions { moveException(exception) }.single(),
            )
        }
    }

// The task of every Reddit API request, an anonymous class in RedditApi.request. Its IOException
// handler throws the farewell message.
private val farewellThrow =
    klass("com.onelouder.baconreader.reddit.RedditApi\$1").method("runTask").point(
        "farewellThrow"
    ) {
        newInstance(IO_EXCEPTION)
        then { stringContains("bittersweet news") }
        then { invokeDirect { owner(IO_EXCEPTION) } }
        then { opcode(Opcode.THROW) }
    }
