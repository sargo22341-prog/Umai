package org.opensources.umai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.lifecycleScope
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.settings.AppPreferences
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.navigation.UmaiApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as UmaiApplication).container
        val preferences = container.preferencesRepository.preferences
            .stateIn(lifecycleScope, SharingStarted.Eagerly, AppPreferences())

        setContent {
            val settings by preferences.collectAsState()
            CompositionLocalProvider(LocalAppContainer provides container) {
                UmaiTheme(
                    themeMode = settings.themeMode,
                    dynamicColor = settings.dynamicColor,
                ) {
                    UmaiApp()
                }
            }
        }
    }
}
