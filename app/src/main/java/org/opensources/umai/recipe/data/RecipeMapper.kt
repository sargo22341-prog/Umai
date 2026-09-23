package org.opensources.umai.recipe.data

import org.opensources.umai.core.format.ApiDates
import org.opensources.umai.core.format.IngredientText
import org.opensources.umai.core.markdown.StepContent
import org.opensources.umai.core.model.Food
import org.opensources.umai.core.model.IngredientFood
import org.opensources.umai.core.model.IngredientUnit
import org.opensources.umai.core.model.Label
import org.opensources.umai.core.model.Nutrition
import org.opensources.umai.core.model.Organizer
import org.opensources.umai.core.model.Paged
import org.opensources.umai.core.model.Recipe
import org.opensources.umai.core.model.RecipeAsset
import org.opensources.umai.core.model.RecipeComment
import org.opensources.umai.core.model.RecipeIngredient
import org.opensources.umai.core.model.RecipeNote
import org.opensources.umai.core.model.RecipeStep
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.network.dto.IngredientFoodDto
import org.opensources.umai.core.network.dto.IngredientFoodListDto
import org.opensources.umai.core.network.dto.IngredientUnitDto
import org.opensources.umai.core.network.dto.LabelDto
import org.opensources.umai.core.network.dto.NutritionDto
import org.opensources.umai.core.network.dto.PaginationDto
import org.opensources.umai.core.network.dto.RecipeCategoryDto
import org.opensources.umai.core.network.dto.RecipeCommentDto
import org.opensources.umai.core.network.dto.RecipeDetailDto
import org.opensources.umai.core.network.dto.RecipeIngredientDto
import org.opensources.umai.core.network.dto.RecipeStepDto
import org.opensources.umai.core.network.dto.RecipeSummaryDto
import org.opensources.umai.core.network.dto.RecipeTagDto
import org.opensources.umai.core.network.dto.RecipeToolDto
import org.opensources.umai.recipe.domain.RecipeMediaFiles

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

    val stepPhotos = RecipeMediaFiles.stepPhotos(assets.map { RecipeAsset(it.name, it.icon, it.fileName) })
    return Recipe(
        summary = summary,
        ingredients = recipeIngredient.map { it.toDomain() },
        steps = recipeInstructions.orEmpty().mapIndexed { index, step ->
            // Step photos are numbered from 1; `step-0` is the ingredients.
            step.toDomain(index).copy(photo = stepPhotos[index + 1])
        },
        nutrition = nutrition?.toDomain()?.takeUnless { it.isEmpty },
        notes = notes.map { RecipeNote(it.title, it.text) },
        showNutrition = settings?.showNutrition ?: false,
        showAssets = settings?.showAssets ?: false,
        assets = assets.map { RecipeAsset(it.name, it.icon, it.fileName) },
        // Only an explicit `true` hides the comments: an instance that omits
        // the settings block should still let the user read and write them.
        commentsDisabled = settings?.disableComments == true,
        ingredientsPhoto = stepPhotos[0],
        mediaVersion = (updatedAt ?: dateUpdated)?.takeIf { it.isNotBlank() },
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
    val ingredient = RecipeIngredient(
        referenceId = referenceId,
        display = "",
        quantity = quantity?.takeIf { it > 0.0 },
        unit = unit?.toDomain(),
        food = food?.toDomain(),
        note = note?.takeIf { it.isNotBlank() },
        sectionTitle = title?.takeIf { it.isNotBlank() },
        originalText = originalText?.takeIf { it.isNotBlank() },
    )
    // Mealie normally pre-renders the line; when it does not, the same rules it
    // applies are used so the display never falls back to an empty row.
    return ingredient.copy(display = display.ifBlank { IngredientText.format(ingredient) })
}

private fun IngredientFoodDto.toDomain() = IngredientFood(
    id = id?.takeIf { it.isNotBlank() },
    name = name,
    pluralName = pluralName?.takeIf { it.isNotBlank() },
)

private fun IngredientUnitDto.toDomain() = IngredientUnit(
    id = id?.takeIf { it.isNotBlank() },
    name = name,
    pluralName = pluralName?.takeIf { it.isNotBlank() },
    abbreviation = abbreviation,
    pluralAbbreviation = pluralAbbreviation?.takeIf { it.isNotBlank() },
    useAbbreviation = useAbbreviation,
    fraction = fraction,
)

/**
 * The reverse mapping: Mealie's "add these ingredients to a shopping list"
 * endpoint expects the very objects it served, so a selected line is rebuilt
 * into the payload it came from.
 */
fun RecipeIngredient.toDto(): RecipeIngredientDto = RecipeIngredientDto(
    quantity = quantity ?: 0.0,
    unit = unit?.let {
        IngredientUnitDto(
            id = it.id,
            name = it.name,
            pluralName = it.pluralName,
            abbreviation = it.abbreviation,
            pluralAbbreviation = it.pluralAbbreviation,
            useAbbreviation = it.useAbbreviation,
            fraction = it.fraction,
        )
    },
    food = food?.let {
        IngredientFoodDto(id = it.id, name = it.name, pluralName = it.pluralName)
    },
    note = note,
    display = display,
    title = sectionTitle,
    originalText = originalText,
    referenceId = referenceId,
)

fun RecipeCommentDto.toDomain(): RecipeComment? {
    val identifier = id.takeIf { it.isNotBlank() } ?: return null
    return RecipeComment(
        id = identifier,
        recipeId = recipeId,
        text = text,
        authorId = userId,
        authorName = user?.fullName?.takeIf { it.isNotBlank() }
            ?: user?.username?.takeIf { it.isNotBlank() }
            ?: "",
        createdAt = ApiDates.parseDateTime(createdAt),
    )
}

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
