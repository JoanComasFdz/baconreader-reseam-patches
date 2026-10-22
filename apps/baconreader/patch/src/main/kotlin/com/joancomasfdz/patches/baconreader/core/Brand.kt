// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.patches.baconreader.core

import app.reseam.patch.settings.Brand
import app.reseam.patch.settings.Color
import app.reseam.patch.settings.ColorScheme

/** Material 3 colors from BaconReader's orange accent, #F57C00. */
val baconReaderBrand =
    Brand(
        name = "BaconReader patches",
        light =
            ColorScheme(
                primary = Color.hex("#8B5000"),
                onPrimary = Color.hex("#FFFFFF"),
                primaryContainer = Color.hex("#FFDCBE"),
                onPrimaryContainer = Color.hex("#6A3C00"),
                secondaryContainer = Color.hex("#FFDCBE"),
                onSecondaryContainer = Color.hex("#5A4230"),
                surface = Color.hex("#FFF8F5"),
                surfaceContainerLow = Color.hex("#FFF1E8"),
                surfaceContainer = Color.hex("#FAEBE0"),
                surfaceContainerHigh = Color.hex("#F4E6DB"),
                surfaceContainerHighest = Color.hex("#EEE0D5"),
                onSurface = Color.hex("#211A14"),
                onSurfaceVariant = Color.hex("#51453A"),
                outline = Color.hex("#837468"),
                outlineVariant = Color.hex("#D5C3B5"),
                error = Color.hex("#BA1A1A"),
                onError = Color.hex("#FFFFFF"),
                errorContainer = Color.hex("#FFDAD6"),
                onErrorContainer = Color.hex("#93000A"),
                scrim = Color.hex("#000000"),
            ),
        dark =
            ColorScheme(
                primary = Color.hex("#FFB870"),
                onPrimary = Color.hex("#4A2800"),
                primaryContainer = Color.hex("#6A3C00"),
                onPrimaryContainer = Color.hex("#FFDCBE"),
                secondaryContainer = Color.hex("#5A4230"),
                onSecondaryContainer = Color.hex("#FFDCBE"),
                surface = Color.hex("#19120C"),
                surfaceContainerLow = Color.hex("#211A14"),
                surfaceContainer = Color.hex("#261E18"),
                surfaceContainerHigh = Color.hex("#312822"),
                surfaceContainerHighest = Color.hex("#3C332C"),
                onSurface = Color.hex("#EFE0D5"),
                onSurfaceVariant = Color.hex("#D5C3B5"),
                outline = Color.hex("#9E8E81"),
                outlineVariant = Color.hex("#51453A"),
                error = Color.hex("#FFB4AB"),
                onError = Color.hex("#690005"),
                errorContainer = Color.hex("#93000A"),
                onErrorContainer = Color.hex("#FFDAD6"),
                scrim = Color.hex("#000000"),
            ),
    )
