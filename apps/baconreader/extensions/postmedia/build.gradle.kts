// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

dependencies {
    compileOnly(project(":apps:baconreader:extensions:common"))
    // Assumed: the app's Retrofit has Call.timeout(), so it is 2.6.0 or newer. Only
    // Response.code() is used, which every Retrofit 2 has.
    compileOnly("com.squareup.retrofit2:retrofit:2.9.0")
}
