// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

dependencies {
    // The version BaconReader 6.1.4 bundles: it has Glide 4.14's RegistryFactory, but not 4.15's
    // DirectResourceLoader.
    compileOnly("com.github.bumptech.glide:glide:4.14.2")
}
