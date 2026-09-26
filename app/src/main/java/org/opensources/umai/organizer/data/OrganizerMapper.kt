package org.opensources.umai.organizer.data

import org.opensources.umai.core.model.Food
import org.opensources.umai.core.model.Organizer
import org.opensources.umai.core.network.dto.IngredientFoodListDto
import org.opensources.umai.core.network.dto.RecipeCategoryDto
import org.opensources.umai.core.network.dto.RecipeTagDto
import org.opensources.umai.core.network.dto.RecipeToolDto

fun RecipeCategoryDto.toDomain(): Organizer? =
    id?.takeIf { it.isNotBlank() }?.let { Organizer(it, name, slug, recipeCount) }

fun RecipeTagDto.toDomain(): Organizer? =
    id?.takeIf { it.isNotBlank() }?.let { Organizer(it, name, slug, recipeCount) }

fun RecipeToolDto.toDomain(): Organizer? =
    id.takeIf { it.isNotBlank() }?.let { Organizer(it, name, slug, recipeCount) }

fun IngredientFoodListDto.toDomain(): Food? =
    id.takeIf { it.isNotBlank() }?.let { Food(it, name, label?.name, label?.color) }
