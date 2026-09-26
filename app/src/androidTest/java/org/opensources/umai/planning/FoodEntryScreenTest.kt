package org.opensources.umai.planning

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.R
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.planning.domain.FoodUnit
import org.opensources.umai.planning.domain.Nutrient
import org.opensources.umai.planning.ui.FoodEntryActions
import org.opensources.umai.planning.ui.FoodEntryScreen
import org.opensources.umai.planning.ui.FoodEntryStep
import org.opensources.umai.planning.ui.FoodEntryUiState
import org.opensources.umai.planning.ui.LabelIssue
import java.time.LocalDate

/** Adding a snack or a drink to the plan: product, nutrition facts, portion. */
@RunWith(AndroidJUnit4::class)
class FoodEntryScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private val day = LocalDate.of(2026, 9, 24)

    private class Recorded {
        var name: String? = null
        var mealType: MealType? = null
        var added = false
        var next = false
        var value: Pair<Nutrient, String>? = null
    }

    private fun render(state: FoodEntryUiState, recorded: Recorded = Recorded()) {
        rule.setContent {
            UmaiTheme {
                FoodEntryScreen(
                    state = state,
                    actions = FoodEntryActions(
                        onLeave = {},
                        onPrevious = {},
                        onNext = { recorded.next = true },
                        onAdd = { recorded.added = true },
                        onNameChange = { recorded.name = it },
                        onMealTypeChange = { recorded.mealType = it },
                        onPhotoPicked = { _, _ -> },
                        onRemovePhoto = {},
                        onReadLabel = {},
                        onCancelReading = {},
                        onDismissLabelIssue = {},
                        onUnitChange = {},
                        onValueChange = { nutrient, text -> recorded.value = nutrient to text },
                        onQuantityChange = {},
                        onDismissError = {},
                    ),
                )
            }
        }
    }

    @Test
    fun theProductNeedsANameToGoOn() {
        val recorded = Recorded()
        render(FoodEntryUiState(date = day), recorded)

        rule.onNodeWithText(string(R.string.create_next)).assertIsNotEnabled()
        rule.onNode(hasSetTextAction() and hasText(string(R.string.food_name))).performTextInput("Cola")
        rule.onNodeWithText(string(R.string.meal_drink)).performClick()

        assertEquals("Cola", recorded.name)
        assertEquals(MealType.DRINK, recorded.mealType)
    }

    @Test
    fun theProductPhotoIsOptional() {
        val recorded = Recorded()
        render(FoodEntryUiState(date = day, name = "Cola"), recorded)

        rule.onNodeWithText(string(R.string.food_photo)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.create_next)).assertIsEnabled().performClick()

        assertTrue(recorded.next)
    }

    @Test
    fun withAModelTheLabelIsPhotographed() {
        render(FoodEntryUiState(date = day, name = "Cola", step = FoodEntryStep.NUTRITION, canReadLabel = true))

        rule.onNodeWithText(string(R.string.food_label_take)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.food_label_intro)).assertIsDisplayed()
    }

    @Test
    fun withoutAModelTheValuesAreTyped() {
        val recorded = Recorded()
        render(FoodEntryUiState(date = day, name = "Cola", step = FoodEntryStep.NUTRITION), recorded)

        rule.onNodeWithText(string(R.string.food_label_no_model)).assertIsDisplayed()
        rule.onNode(hasSetTextAction() and hasText(string(R.string.nutrition_energy))).performTextInput("42")

        assertEquals(Nutrient.ENERGY to "42", recorded.value)
    }

    @Test
    fun aLabelBeingReadCanBeCancelled() {
        render(
            FoodEntryUiState(date = day, name = "Cola", step = FoodEntryStep.NUTRITION, canReadLabel = true, readingLabel = true),
        )

        rule.onNodeWithText(string(R.string.food_label_reading)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.action_cancel)).assertIsDisplayed()
    }

    @Test
    fun aLabelThatCouldNotBeReadSaysWhy() {
        render(
            FoodEntryUiState(
                date = day,
                name = "Cola",
                step = FoodEntryStep.NUTRITION,
                canReadLabel = true,
                labelIssue = LabelIssue.NOTHING_FOUND,
            ),
        )

        rule.onNodeWithText(string(R.string.food_label_nothing)).assertIsDisplayed()
    }

    @Test
    fun thePortionGivesItsCaloriesAndIsAdded() {
        val recorded = Recorded()
        render(
            FoodEntryUiState(
                date = day,
                name = "Cola",
                step = FoodEntryStep.PORTION,
                unit = FoodUnit.MILLILITRE,
                values = mapOf(Nutrient.ENERGY to "42"),
                quantity = "330",
            ),
            recorded,
        )

        rule.onNodeWithText(string(R.string.food_calories_total, 139)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.action_add)).performClick()

        assertTrue(recorded.added)
    }

    @Test
    fun withoutEnergyThePortionSaysItHasNoCalories() {
        render(FoodEntryUiState(date = day, name = "Pomme", step = FoodEntryStep.PORTION))

        rule.onNodeWithText(string(R.string.food_calories_missing, "g")).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(string(R.string.action_add)).assertIsEnabled()
    }
}
