package org.opensources.umai.planning

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
import org.opensources.umai.core.model.Organizer
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.ui.theme.UmaiTheme
import org.opensources.umai.planning.domain.DishCourse
import org.opensources.umai.planning.ui.DishTypeRow
import org.opensources.umai.planning.ui.DishTypesScreen
import org.opensources.umai.planning.ui.DishTypesUiState

/** The course the automatic planning sees in each category and tag, and correcting it. */
@RunWith(AndroidJUnit4::class)
class DishTypesScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private val chosen = mutableListOf<Pair<String, DishCourse?>>()

    private fun render(state: DishTypesUiState) {
        rule.setContent {
            UmaiTheme {
                DishTypesScreen(state = state, onBack = {}, onChoose = { id, course -> chosen += id to course }, onRetry = {})
            }
        }
    }

    private val rows = listOf(
        DishTypeRow(Organizer("c1", "Desserts", "desserts"), isTag = false, detected = DishCourse.DESSERT, chosen = null),
        DishTypeRow(Organizer("t1", "Italien", "italien"), isTag = true, detected = null, chosen = null),
    )

    @Test
    fun eachCategoryShowsTheCourseSeenInIt() {
        render(DishTypesUiState(rows = rows, loading = false))

        rule.onNodeWithText("Desserts").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.dish_types_detected, string(R.string.dish_types_category))).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.dish_types_neutral, string(R.string.dish_types_tag))).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.dish_course_any)).assertIsDisplayed()
    }

    @Test
    fun aTagCanBeMadeADish() {
        render(DishTypesUiState(rows = rows, loading = false))

        rule.onNodeWithText(string(R.string.dish_course_any)).performClick()
        rule.onNodeWithText(string(R.string.dish_course_main)).performClick()

        assertEquals(listOf("t1" to DishCourse.MAIN), chosen)
    }

    @Test
    fun anInstanceWithoutCategoriesSaysSo() {
        render(DishTypesUiState(rows = emptyList(), loading = false))
        rule.onNodeWithText(string(R.string.dish_types_empty_title)).assertIsDisplayed()
    }

    @Test
    fun anUnreachableServerOffersToRetry() {
        render(DishTypesUiState(loading = false, error = NetworkError.Unreachable))
        rule.onNodeWithText(string(R.string.action_retry)).assertIsDisplayed()
    }
}
