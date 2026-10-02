package org.opensources.umai.core.model

import java.time.LocalDate

/** The meal slots Mealie exposes through `PlanEntryType`. */
enum class MealType(val apiValue: String) {
    BREAKFAST("breakfast"),
    LUNCH("lunch"),
    DINNER("dinner"),
    SIDE("side"),
    SNACK("snack"),
    DRINK("drink"),
    DESSERT("dessert");

    companion object {
        fun fromApi(value: String): MealType =
            entries.firstOrNull { it.apiValue.equals(value, ignoreCase = true) } ?: DINNER

        /** Order used when several meals share the same day. */
        val displayOrder: List<MealType> = listOf(BREAKFAST, LUNCH, DINNER, SNACK, SIDE, DESSERT, DRINK)
    }
}

data class MealPlanEntry(
    val id: Int,
    val date: LocalDate,
    val type: MealType,
    val title: String,
    val text: String,
    val recipe: RecipeSummary?,
    val groupId: String?,
    val userId: String?,
    /** How many servings of the recipe, or portions of the food, were eaten. */
    val servings: Int = 1,
) {
    init {
        require(servings >= 1) { "A plan entry has at least one serving" }
    }

    /** A plan entry is either a recipe reference or a free-text note. */
    val displayTitle: String get() = recipe?.name ?: title.ifBlank { text }
}
