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
