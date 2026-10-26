// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

// The e2e tests' own bundle: a workspace builds one bundle, so test-only patches live in a
// workspace of their own
// and never reach the release, which builds the root's.
pluginManagement {
    repositories {
        exclusiveContent {
            forRepository { maven("https://git.reseam.app/api/packages/reseam/maven") }
            filter { includeGroupAndSubgroups("app.reseam") }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    // The root's plugin version, so a Reseam update moves both bundles.
    val reseam =
        Regex("""id\("app\.reseam\.workspace"\) version "([^"]+)"""")
            .find(settingsDir.resolve("../settings.gradle.kts").readText())!!
            .groupValues[1]
    plugins { id("app.reseam.workspace") version reseam }
}

plugins {
    id("app.reseam.workspace")
}

rootProject.name = "baconreader-e2e"
