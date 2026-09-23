package org.opensources.umai.provider

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.R
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.provider.jow.JowProvider
import org.opensources.umai.provider.ui.ProviderScreen
import org.opensources.umai.provider.ui.ProviderUiState
import org.opensources.umai.provider.ui.ProvidersScreen

/** Settings → Providers, and the page of one provider. */
@RunWith(AndroidJUnit4::class)
class ProviderScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun render(state: ProviderUiState, onImportsMediaChange: (Boolean) -> Unit = {}) {
        rule.setContent {
            UmaiTheme {
                ProviderScreen(state = state, onBack = {}, onImportsMediaChange = onImportsMediaChange)
            }
        }
    }

    @Test
    fun theListNamesEveryProvider() {
        var opened: String? = null
        rule.setContent {
            UmaiTheme { ProvidersScreen(providers = listOf(JowProvider), onBack = {}, onOpenProvider = { opened = it }) }
        }

        rule.onNodeWithText("Jow").performClick()

        assertEquals("jow", opened)
    }

    @Test
    fun fetchingTheMediaOnImportCanBeTurnedOff() {
        var enabled: Boolean? = null
        render(ProviderUiState(provider = JowProvider, importsMedia = true), onImportsMediaChange = { enabled = it })

        rule.onNodeWithText(string(R.string.provider_import_media)).performClick()

        assertEquals(false, enabled)
    }

    @Test
    fun anUnknownProviderSaysSo() {
        render(ProviderUiState(provider = null))

        rule.onNodeWithText(string(R.string.provider_unknown)).assertIsDisplayed()
    }
}
