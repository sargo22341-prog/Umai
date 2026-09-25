package org.opensources.umai.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class MealPlanEntryDto(
    val id: Int = 0,
    val date: String = "",
    val entryType: String = "dinner",
    val title: String = "",
    val text: String = "",
    val recipeId: String? = null,
    val groupId: String? = null,
    val userId: String? = null,
    val householdId: String? = null,
    val recipe: RecipeSummaryDto? = null,
)

@Serializable
data class CreateMealPlanEntryDto(
    val date: String,
    val entryType: String,
    val title: String = "",
    val text: String = "",
    val recipeId: String? = null,
)

@Serializable
data class UpdateMealPlanEntryDto(
    val id: Int,
    val date: String,
    val entryType: String,
    val title: String = "",
    val text: String = "",
    val recipeId: String? = null,
    val groupId: String,
    val userId: String,
)

/**
 * Mirrors `PlanRulesOut`: a rule of the household for its meal plan, such as
 * "on Friday, dinner is fish". [day] and [entryType] are `unset` for a rule
 * that applies to every day or every meal; [queryFilterString] is a filter
 * of the `queryFilter` mini-language the recipes must match.
 */
@Serializable
data class PlanRuleDto(
    val id: String = "",
    val day: String = "unset",
    val entryType: String = "unset",
    val queryFilterString: String = "",
)
