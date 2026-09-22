package org.opensources.umai.core.di

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Makes the hand-written graph reachable from any screen without threading it
 * through every composable signature.
 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer was not provided; wrap the content in CompositionLocalProvider.")
}
