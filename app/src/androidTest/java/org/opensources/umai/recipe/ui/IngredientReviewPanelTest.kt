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
import org.opensources.umai.recipe.domain.IngredientChoice
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

    @Test fun editingAFoodRequestsSuggestionsAndEmptySearchHasFeedback() {
        var searched = ""
        rule.setContent { UmaiTheme { Column {
            IngredientReviewPanel(RecipeDraft("test", ingredients = listOf(line)),
                IngredientReviewState(reviewing = line, searched = true),
                IngredientReviewActions(search = { food, _ -> searched = food }), enabled = true)
        } } }
        rule.onNodeWithText(string(R.string.ingredients_no_choices)).assertExists()
        rule.onNodeWithText(string(R.string.ingredients_food)).performTextReplacement("sel marin")
        rule.runOnIdle { assertEquals("sel marin", searched) }
    }

    @Test fun aTextOnlyServerIngredientGetsMetricFieldsWithoutRunningAI() {
        val imported = DraftIngredient("150 g (1 Tasse) de cacahuètes grillées",
            analysis = IngredientAnalysis(0.0, null, note = "150 g (1 Tasse) de cacahuètes grillées"))
        rule.setContent { UmaiTheme { Column {
            IngredientReviewPanel(RecipeDraft("test", ingredients = listOf(imported)),
                IngredientReviewState(reviewing = imported), IngredientReviewActions(), enabled = true)
        } } }
        rule.onNodeWithText("150.0").assertExists()
        rule.onNodeWithText("cacahuètes grillées").assertExists()
        rule.onNodeWithText("g").assertExists()
    }

    @Test fun validatedQuantitiesOfferEditingWithoutImplyingAnotherReview() {
        val validated = line.copy(analysis = IngredientAnalysis(200.0, IngredientChoice("flour"), IngredientChoice("g")))
        rule.setContent { UmaiTheme { Column {
            IngredientReviewPanel(RecipeDraft("test", ingredients = listOf(validated)),
                IngredientReviewState(), IngredientReviewActions(), enabled = true)
        } } }
        rule.onNodeWithText(context.getString(R.string.ingredients_analyzed, 1)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.ingredients_edit_line, validated.text)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.ingredients_review_line, validated.text)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.ingredients_unscaled, 0)).assertDoesNotExist()
    }
}
