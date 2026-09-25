pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // The settings variant so the report also covers the plugins resolved here.
    // Its version is inline because the version catalog does not exist yet while
    // settings are evaluated.
    id("io.github.ben-manes.versions.settings") version "0.64.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // NewPipeExtractor and its JSON parser are published on JitPack only;
        // nothing else is ever resolved from there.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroup("com.github.TeamNewPipe") }
        }
    }
}

rootProject.name = "umai"
include(":app")
