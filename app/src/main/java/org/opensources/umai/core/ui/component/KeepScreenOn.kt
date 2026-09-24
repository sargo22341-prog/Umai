package org.opensources.umai.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Keeps the display awake for as long as this composable is on screen, and
 * restores the normal behaviour on exit even if the screen is left abruptly.
 */
@Composable
fun KeepScreenOn(enabled: Boolean = true) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
