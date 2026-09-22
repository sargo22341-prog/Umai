import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.specs.Spec

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
}

// Only plain release versions are offered as updates: an alpha, beta, rc, dev or
// snapshot build of AGP, Compose or OkHttp has no place in a shipped client.
private fun String.isNonStableVersion(): Boolean {
    val hasStableKeyword = listOf("RELEASE", "FINAL", "GA").any { keyword -> uppercase().contains(keyword) }
    val stableVersion = "^[0-9,.v-]+(-r)?$".toRegex()

    return !hasStableKeyword && !stableVersion.matches(this)
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates") {
    revision = "release"
    gradleReleaseChannel = "current"

    // The report is built from live version lookups, so its result must never be replayed from the
    // configuration cache: a cached entry freezes whatever the network answered when it was stored,
    // and a single DNS hiccup would then be repeated by every later run without touching the
    // network. Declaring the task incompatible makes Gradle skip the cache for this invocation only.
    notCompatibleWithConfigurationCache(
        "dependencyUpdates queries repositories for the latest versions on every run.",
    )

    // These two classpaths are Kotlin-internal and pin a compatibility artifact on purpose, so the
    // report lists a version that is deliberately behind the compiler's own. Reporting them says
    // nothing about what this project declares.
    filterConfigurations =
        Spec<Configuration> { configuration ->
            configuration.name !in
                setOf(
                    "kotlinBuildToolsApiClasspath",
                    "kotlinAbiValidationCompatClasspath",
                )
        }

    rejectVersionIf {
        candidate.version.isNonStableVersion()
    }
}
