package org.opensources.umai.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RecipeSummaryDto(
    val id: String? = null,
    val userId: String? = null,
    val householdId: String? = null,
    val groupId: String? = null,
    val name: String? = null,
    val slug: String = "",
    @Serializable(with = ScalarAsStringSerializer::class) val image: String? = null,
    val recipeServings: Double = 0.0,
    val recipeYieldQuantity: Double = 0.0,
    val recipeYield: String? = null,
    val totalTime: String? = null,
    val prepTime: String? = null,
    val cookTime: String? = null,
    val performTime: String? = null,
    val description: String? = "",
    @SerialName("recipeCategory") val categories: List<RecipeCategoryDto>? = emptyList(),
    val tags: List<RecipeTagDto>? = emptyList(),
    val tools: List<RecipeToolDto> = emptyList(),
    val rating: Double? = null,
    val orgURL: String? = null,
    val dateAdded: String? = null,
    val dateUpdated: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val lastMade: String? = null,
)

@Serializable
data class RecipeDetailDto(
    val id: String? = null,
    val userId: String? = null,
    val householdId: String? = null,
    val groupId: String? = null,
    val name: String? = null,
    val slug: String = "",
    @Serializable(with = ScalarAsStringSerializer::class) val image: String? = null,
    val recipeServings: Double = 0.0,
    val recipeYieldQuantity: Double = 0.0,
    val recipeYield: String? = null,
    val totalTime: String? = null,
    val prepTime: String? = null,
    val cookTime: String? = null,
    val performTime: String? = null,
    val description: String? = "",
    @SerialName("recipeCategory") val categories: List<RecipeCategoryDto>? = emptyList(),
    val tags: List<RecipeTagDto>? = emptyList(),
    val tools: List<RecipeToolDto> = emptyList(),
    val rating: Double? = null,
    val orgURL: String? = null,
    val dateAdded: String? = null,
    val dateUpdated: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val lastMade: String? = null,
    val recipeIngredient: List<RecipeIngredientDto> = emptyList(),
    val recipeInstructions: List<RecipeStepDto>? = emptyList(),
    val nutrition: NutritionDto? = null,
    val settings: RecipeSettingsDto? = null,
    val assets: List<RecipeAssetDto> = emptyList(),
    val notes: List<RecipeNoteDto> = emptyList(),
)

@Serializable
data class RecipeStepDto(
    val id: String? = null,
    val title: String? = "",
    val summary: String? = "",
    val text: String = "",
    val ingredientReferences: List<IngredientReferenceDto> = emptyList(),
)

@Serializable
data class IngredientReferenceDto(val referenceId: String? = null)

@Serializable
data class RecipeIngredientDto(
    val quantity: Double? = 0.0,
    val unit: IngredientUnitDto? = null,
    val food: IngredientFoodDto? = null,
    val note: String? = "",
    val display: String = "",
    val title: String? = null,
    val originalText: String? = null,
    val referenceId: String? = null,
)

@Serializable
data class IngredientFoodDto(
    val id: String? = null,
    val name: String = "",
    val pluralName: String? = null,
    val description: String = "",
    val labelId: String? = null,
    val label: LabelDto? = null,
)

@Serializable
data class IngredientUnitDto(
    val id: String? = null,
    val name: String = "",
    val pluralName: String? = null,
    val abbreviation: String = "",
    val pluralAbbreviation: String? = null,
    val useAbbreviation: Boolean = false,
    val fraction: Boolean = true,
)

@Serializable
data class NutritionDto(
    val calories: String? = null,
    val carbohydrateContent: String? = null,
    val cholesterolContent: String? = null,
    val fatContent: String? = null,
    val fiberContent: String? = null,
    val proteinContent: String? = null,
    val saturatedFatContent: String? = null,
    val sodiumContent: String? = null,
    val sugarContent: String? = null,
    val transFatContent: String? = null,
    val unsaturatedFatContent: String? = null,
)

@Serializable
data class RecipeSettingsDto(
    val public: Boolean = false,
    val showNutrition: Boolean = false,
    val showAssets: Boolean = false,
    val landscapeView: Boolean = false,
    val disableComments: Boolean = true,
    val locked: Boolean = false,
)

@Serializable
data class RecipeAssetDto(
    val name: String = "",
    val icon: String = "",
    val fileName: String? = null,
)

@Serializable
data class RecipeNoteDto(
    val title: String = "",
    val text: String = "",
)

@Serializable
data class RecipeCategoryDto(
    val id: String? = null,
    val groupId: String? = null,
    val name: String = "",
    val slug: String = "",
    val recipeCount: Int = 0,
)

@Serializable
data class RecipeTagDto(
    val id: String? = null,
    val groupId: String? = null,
    val name: String = "",
    val slug: String = "",
    val recipeCount: Int = 0,
)

@Serializable
data class RecipeToolDto(
    val id: String = "",
    val groupId: String? = null,
    val name: String = "",
    val slug: String = "",
    val recipeCount: Int = 0,
)

@Serializable
data class LabelDto(
    val id: String = "",
    val name: String = "",
    val color: String = "#959595",
    val groupId: String? = null,
)

@Serializable
data class IngredientFoodListDto(
    val id: String = "",
    val name: String = "",
    val pluralName: String? = null,
    val label: LabelDto? = null,
)

/** Mirrors `UpdateImageResponse`: the new cache token of the recipe picture. */
@Serializable
data class UpdateImageResponseDto(
    val image: String = "",
)
