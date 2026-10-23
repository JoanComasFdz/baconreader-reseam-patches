// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

// Assumed: the app's Retrofit has Call.timeout(), so it is 2.6.0 or newer. Only Call.execute(),
// Response.code() and Response.body() are used, which every Retrofit 2 has.
val retrofit = "com.squareup.retrofit2:retrofit:2.9.0"

dependencies {
    compileOnly(project(":apps:baconreader:extensions:common"))
    // The version BaconReader 6.1.4 bundles.
    compileOnly("com.github.bumptech.glide:glide:4.14.2")
    compileOnly(retrofit)
    // The RedGifsManager stub returns a Retrofit call.
    "stubsCompileOnly"(retrofit)
}
