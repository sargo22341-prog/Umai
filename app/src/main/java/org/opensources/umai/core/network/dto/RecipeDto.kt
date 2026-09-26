package org.opensources.umai.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What a recipe of a list and a whole recipe both carry, mapped the same way. */
interface RecipeSummaryFields {
    val id: String?
    val name: String?
    val slug: String
    val image: String?
    val recipeServings: Double
    val recipeYield: String?
    val totalTime: String?
    val prepTime: String?
    val cookTime: String?
    val performTime: String?
    val description: String?
    val categories: List<RecipeCategoryDto>?
    val tags: List<RecipeTagDto>?
    val tools: List<RecipeToolDto>
    val rating: Double?
    val orgURL: String?
    val dateAdded: String?
    val lastMade: String?
}

@Serializable
data class RecipeSummaryDto(
    override val id: String? = null,
    override val name: String? = null,
    override val slug: String = "",
    @Serializable(with = ScalarAsStringSerializer::class) override val image: String? = null,
    override val recipeServings: Double = 0.0,
    override val recipeYield: String? = null,
    override val totalTime: String? = null,
    override val prepTime: String? = null,
    override val cookTime: String? = null,
    override val performTime: String? = null,
    override val description: String? = "",
    @SerialName("recipeCategory") override val categories: List<RecipeCategoryDto>? = emptyList(),
    override val tags: List<RecipeTagDto>? = emptyList(),
    override val tools: List<RecipeToolDto> = emptyList(),
    override val rating: Double? = null,
    override val orgURL: String? = null,
    override val dateAdded: String? = null,
    override val lastMade: String? = null,
) : RecipeSummaryFields

@Serializable
data class RecipeDetailDto(
    override val id: String? = null,
    override val name: String? = null,
    override val slug: String = "",
    @Serializable(with = ScalarAsStringSerializer::class) override val image: String? = null,
    override val recipeServings: Double = 0.0,
    override val recipeYield: String? = null,
    override val totalTime: String? = null,
    override val prepTime: String? = null,
    override val cookTime: String? = null,
    override val performTime: String? = null,
    override val description: String? = "",
    @SerialName("recipeCategory") override val categories: List<RecipeCategoryDto>? = emptyList(),
    override val tags: List<RecipeTagDto>? = emptyList(),
    override val tools: List<RecipeToolDto> = emptyList(),
    override val rating: Double? = null,
    override val orgURL: String? = null,
    override val dateAdded: String? = null,
    override val lastMade: String? = null,
    val dateUpdated: String? = null,
    val updatedAt: String? = null,
    val recipeIngredient: List<RecipeIngredientDto> = emptyList(),
    val recipeInstructions: List<RecipeStepDto>? = emptyList(),
    val nutrition: NutritionDto? = null,
    val settings: RecipeSettingsDto? = null,
    val assets: List<RecipeAssetDto> = emptyList(),
    val notes: List<RecipeNoteDto> = emptyList(),
) : RecipeSummaryFields

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
    val showNutrition: Boolean = false,
    val showAssets: Boolean = false,
    val disableComments: Boolean = true,
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
