package org.opensources.umai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.opensources.umai.cooking.data.CookingStepRequest
import org.opensources.umai.cooking.data.TimerIntents
import org.opensources.umai.core.di.LocalAppContainer
import org.opensources.umai.core.session.SessionState
import org.opensources.umai.core.settings.AppPreferences
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.navigation.UmaiApp
import org.opensources.umai.recipe.data.ImportNotifications
import org.opensources.umai.recipe.data.ImportRequest
import org.opensources.umai.recipe.domain.RecipeLinks

class MainActivity : ComponentActivity() {

    /** A recipe page shared to the app, waiting for the import to open. */
    private val sharedUrl = MutableStateFlow<String?>(null)

    /** A cooking mode asked for by a timer notification, waiting to open. */
    private val cookingRequest = MutableStateFlow<CookingStepRequest?>(null)

    /** What an import notification asks to open, waiting to. */
    private val importRequest = MutableStateFlow<ImportRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // A share is handled once: not again when the activity is recreated.
        if (savedInstanceState == null) {
            sharedUrl.value = intent.sharedRecipeUrl()
            cookingRequest.value = TimerIntents.cookingStep(intent)
            importRequest.value = importRequestOf(intent)
        }

        val container = (application as UmaiApplication).container
        val preferences = container.preferencesRepository.preferences
            .stateIn(lifecycleScope, SharingStarted.Eagerly, AppPreferences())

        setContent {
            val settings by preferences.collectAsState()
            val shared by sharedUrl.collectAsState()
            val cooking by cookingRequest.collectAsState()
            val importAsked by importRequest.collectAsState()
            CompositionLocalProvider(LocalAppContainer provides container) {
                UmaiTheme(
                    themeMode = settings.themeMode,
                    dynamicColor = settings.dynamicColor,
                ) {
                    UmaiApp(
                        sharedUrl = shared,
                        onSharedUrlHandled = { sharedUrl.value = null },
                        cookingRequest = cooking,
                        onCookingRequestHandled = { cookingRequest.value = null },
                        importRequest = importAsked,
                        onImportRequestHandled = { importRequest.value = null },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // A password session in use is renewed before it can expire. On a cold
        // start the session is still being read: the refresh waits for it.
        val container = (application as UmaiApplication).container
        container.applicationScope.launch {
            container.sessionManager.state.first { it !is SessionState.Loading }
            container.authRepository.refreshIfDue()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.sharedRecipeUrl()?.let { sharedUrl.value = it }
        TimerIntents.cookingStep(intent)?.let { cookingRequest.value = it }
        importRequestOf(intent)?.let { importRequest.value = it }
    }

    /** The recipe an import notification opens is how that import ended: it is seen. */
    private fun importRequestOf(intent: Intent): ImportRequest? = ImportNotifications.request(intent)?.also { request ->
        if (request is ImportRequest.OpenRecipe) (application as UmaiApplication).container.recipeImports.seen(request.slug)
    }

    private fun Intent.sharedRecipeUrl(): String? =
        if (action == Intent.ACTION_SEND) RecipeLinks.extract(getStringExtra(Intent.EXTRA_TEXT)) else null
}
