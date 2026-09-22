package org.opensources.umai.recipe.data

import org.opensources.umai.core.markdown.StepContent
import org.opensources.umai.core.model.Food
import org.opensources.umai.core.model.Label
import org.opensources.umai.core.model.Nutrition
import org.opensources.umai.core.model.Organizer
import org.opensources.umai.core.model.Paged
import org.opensources.umai.core.model.Recipe
import org.opensources.umai.core.model.RecipeAsset
import org.opensources.umai.core.model.RecipeIngredient
import org.opensources.umai.core.model.RecipeNote
import org.opensources.umai.core.model.RecipeStep
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.network.dto.IngredientFoodListDto
import org.opensources.umai.core.network.dto.LabelDto
import org.opensources.umai.core.network.dto.NutritionDto
import org.opensources.umai.core.network.dto.PaginationDto
import org.opensources.umai.core.network.dto.RecipeCategoryDto
import org.opensources.umai.core.network.dto.RecipeDetailDto
import org.opensources.umai.core.network.dto.RecipeIngredientDto
import org.opensources.umai.core.network.dto.RecipeStepDto
import org.opensources.umai.core.network.dto.RecipeSummaryDto
import org.opensources.umai.core.network.dto.RecipeTagDto
import org.opensources.umai.core.network.dto.RecipeToolDto

/**
 * DTO to domain conversion. Anything the UI should never have to reason about
 * (nullable names, empty ids, markup embedded in step text) is resolved here.
 */
fun RecipeSummaryDto.toDomain(): RecipeSummary? {
    val identifier = id?.takeIf { it.isNotBlank() } ?: return null
    return RecipeSummary(
        id = identifier,
        slug = slug,
        name = name?.takeIf { it.isNotBlank() } ?: slug,
        description = description.orEmpty(),
        imageToken = image,
        servings = recipeServings,
        yieldText = recipeYield?.takeIf { it.isNotBlank() },
        totalTime = totalTime?.takeIf { it.isNotBlank() },
        prepTime = prepTime?.takeIf { it.isNotBlank() },
        cookTime = cookTime?.takeIf { it.isNotBlank() },
        performTime = performTime?.takeIf { it.isNotBlank() },
        categories = categories.orEmpty().mapNotNull { it.toDomain() },
        tags = tags.orEmpty().mapNotNull { it.toDomain() },
        tools = tools.mapNotNull { it.toDomain() },
        rating = rating?.takeIf { it > 0.0 },
        sourceUrl = orgURL?.takeIf { it.isNotBlank() },
        dateAdded = dateAdded,
        lastMade = lastMade,
    )
}

fun RecipeDetailDto.toDomain(): Recipe? {
    val summary = RecipeSummaryDto(
        id = id,
        userId = userId,
        householdId = householdId,
        groupId = groupId,
        name = name,
        slug = slug,
        image = image,
        recipeServings = recipeServings,
        recipeYieldQuantity = recipeYieldQuantity,
        recipeYield = recipeYield,
        totalTime = totalTime,
        prepTime = prepTime,
        cookTime = cookTime,
        performTime = performTime,
        description = description,
        categories = categories,
        tags = tags,
        tools = tools,
        rating = rating,
        orgURL = orgURL,
        dateAdded = dateAdded,
        dateUpdated = dateUpdated,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastMade = lastMade,
    ).toDomain() ?: return null

    return Recipe(
        summary = summary,
        ingredients = recipeIngredient.map { it.toDomain() },
        steps = recipeInstructions.orEmpty().mapIndexed { index, step -> step.toDomain(index) },
        nutrition = nutrition?.toDomain()?.takeUnless { it.isEmpty },
        notes = notes.map { RecipeNote(it.title, it.text) },
        showNutrition = settings?.showNutrition ?: false,
        showAssets = settings?.showAssets ?: false,
        assets = assets.map { RecipeAsset(it.name, it.icon, it.fileName) },
    )
}

private fun RecipeStepDto.toDomain(index: Int): RecipeStep {
    val content = StepContent.parse(text)
    return RecipeStep(
        id = id?.takeIf { it.isNotBlank() } ?: "step-$index",
        title = title?.takeIf { it.isNotBlank() },
        text = content.text,
        images = content.imageSources,
        ingredientReferenceIds = ingredientReferences.mapNotNull { it.referenceId },
    )
}

private fun RecipeIngredientDto.toDomain(): RecipeIngredient {
    val unitLabel = unit?.let { if (it.useAbbreviation && it.abbreviation.isNotBlank()) it.abbreviation else it.name }
    return RecipeIngredient(
        referenceId = referenceId,
        display = display.ifBlank { fallbackDisplay(unitLabel) },
        quantity = quantity?.takeIf { it > 0.0 },
        unit = unitLabel?.takeIf { it.isNotBlank() },
        food = food?.name?.takeIf { it.isNotBlank() },
        note = note?.takeIf { it.isNotBlank() },
        sectionTitle = title?.takeIf { it.isNotBlank() },
        foodId = food?.id,
        unitId = unit?.id,
    )
}

private fun RecipeIngredientDto.fallbackDisplay(unitLabel: String?): String =
    listOfNotNull(
        org.opensources.umai.core.format.QuantityText.format(quantity).takeIf { it.isNotBlank() },
        unitLabel?.takeIf { it.isNotBlank() },
        food?.name?.takeIf { it.isNotBlank() },
        note?.takeIf { it.isNotBlank() },
        originalText?.takeIf { it.isNotBlank() && food == null && note.isNullOrBlank() },
    ).joinToString(" ")

private fun NutritionDto.toDomain() = Nutrition(
    calories = calories,
    carbohydrates = carbohydrateContent,
    fat = fatContent,
    protein = proteinContent,
    fiber = fiberContent,
    sugar = sugarContent,
    sodium = sodiumContent,
    cholesterol = cholesterolContent,
    saturatedFat = saturatedFatContent,
    transFat = transFatContent,
    unsaturatedFat = unsaturatedFatContent,
)

fun RecipeCategoryDto.toDomain(): Organizer? =
    id?.takeIf { it.isNotBlank() }?.let { Organizer(it, name, slug, recipeCount) }

fun RecipeTagDto.toDomain(): Organizer? =
    id?.takeIf { it.isNotBlank() }?.let { Organizer(it, name, slug, recipeCount) }

fun RecipeToolDto.toDomain(): Organizer? =
    id.takeIf { it.isNotBlank() }?.let { Organizer(it, name, slug, recipeCount) }

fun IngredientFoodListDto.toDomain(): Food? =
    id.takeIf { it.isNotBlank() }?.let { Food(it, name, label?.name, label?.color) }

fun LabelDto.toDomain(): Label? =
    id.takeIf { it.isNotBlank() }?.let { Label(it, name, color) }

fun <D, T> PaginationDto<D>.toPaged(transform: (D) -> T?): Paged<T> = Paged(
    items = items.mapNotNull(transform),
    page = page,
    totalPages = totalPages,
    total = total,
)
