package org.opensources.umai.planning.domain

import org.opensources.umai.core.model.MealType
import java.time.DayOfWeek

/**
 * A rule of the household's meal plan in Mealie ("on Friday, dinner is
 * fish"), with the recipes that satisfy it. [day] and [type] are `null` for a
 * rule that applies to any day or any meal.
 */
data class PlanRule(val day: DayOfWeek?, val type: MealType?, val recipeIds: Set<String>) {
    fun appliesTo(slot: MealSlot): Boolean =
        (day == null || day == slot.date.dayOfWeek) && (type == null || type == slot.type)
}

/**
 * Whether [recipeId] may go on [slot]: it must satisfy every rule covering
 * that day and meal, as in Mealie's own random planning, which joins their
 * filters with AND. A meal no rule covers takes any dish.
 */
fun List<PlanRule>.allow(slot: MealSlot, recipeId: String): Boolean =
    filter { it.appliesTo(slot) }.all { recipeId in it.recipeIds }
