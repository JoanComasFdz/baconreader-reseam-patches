pluginManagement {
    repositories {
        // The Reseam plugin is only on git.reseam.app. Without exclusiveContent, Gradle asks the Plugin Portal first,
        // which redirects to Maven Central, past the cloud sessions' mirror, and fails the build on a 429.
        exclusiveContent {
            forRepository { maven("https://git.reseam.app/api/packages/reseam/maven") }
            filter { includeGroupAndSubgroups("app.reseam") }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    (System.getenv("RESEAM_WORKSPACE") ?: providers.gradleProperty("reseam.workspace").orNull)
        ?.takeIf { it.isNotBlank() }
        ?.let { includeBuild(it) }
}

plugins {
    id("app.reseam.workspace") version "0.29.0"
}

rootProject.name = "baconreader-patches"
