package org.opensources.umai.recipe.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.R
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.recipe.domain.DraftIngredient
import org.opensources.umai.recipe.domain.IngredientAnalysis
import org.opensources.umai.recipe.domain.RecipeDraft

@RunWith(AndroidJUnit4::class)
class IngredientReviewPanelTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun string(id: Int) = context.getString(id)
    private val line = DraftIngredient("200 g flour")

    @Test fun unparsedIngredientsOfferAnalysis() {
        var runs = 0
        rule.setContent { UmaiTheme { Column {
            IngredientReviewPanel(RecipeDraft("test", ingredients = listOf(line)), IngredientReviewState(),
                IngredientReviewActions(analyze = { runs++ }), enabled = true)
        } } }
        rule.onNodeWithText(string(R.string.ingredients_analyze)).performClick()
        assertEquals(1, runs)
    }

    @Test fun manualCorrectionAcceptsADecimalQuantityAndFood() {
        var confirmed: IngredientAnalysis? = null
        rule.setContent { UmaiTheme { Column {
            IngredientReviewPanel(RecipeDraft("test", ingredients = listOf(line)), IngredientReviewState(reviewing = line),
                IngredientReviewActions(confirm = { confirmed = it }), enabled = true)
        } } }
        rule.onNodeWithText(string(R.string.ingredients_review_title)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.ingredients_quantity)).performTextReplacement("200,5")
        rule.onNodeWithText(string(R.string.ingredients_food)).performTextReplacement("flour")
        rule.onNodeWithText(string(R.string.ingredients_unit)).performTextReplacement("g")
        rule.onNodeWithText(string(R.string.ingredients_confirm)).performClick()
        assertEquals(200.5, confirmed?.quantity)
        assertEquals("flour", confirmed?.food?.name)
        assertTrue(confirmed?.isResolved == true)
    }

    @Test fun aQuantityCanBeLeftExplicitlyUnscaled() {
        var confirmed: IngredientAnalysis? = null
        rule.setContent { UmaiTheme { Column {
            IngredientReviewPanel(RecipeDraft("test", ingredients = listOf(line)), IngredientReviewState(reviewing = line),
                IngredientReviewActions(confirm = { confirmed = it }), enabled = true)
        } } }
        rule.onNodeWithText(string(R.string.ingredients_keep_text)).performClick()
        assertTrue(confirmed?.keptAsText == true)
        assertTrue(confirmed?.isScalable == false)
        assertEquals(line.text, confirmed?.note)
    }
}
