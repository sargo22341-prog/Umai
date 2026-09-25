package org.opensources.umai.planning.domain

import org.opensources.umai.core.model.IngredientFood
import org.opensources.umai.core.model.Organizer
import org.opensources.umai.core.model.RecipeIngredient
import org.opensources.umai.core.model.RecipeSummary

fun summary(
    id: String,
    name: String = id,
    categories: List<Organizer> = emptyList(),
    tags: List<Organizer> = emptyList(),
    rating: Double? = null,
    lastMade: String? = null,
) = RecipeSummary(
    id = id,
    slug = id,
    name = name,
    description = "",
    imageToken = null,
    servings = 4.0,
    yieldText = null,
    totalTime = null,
    prepTime = null,
    cookTime = null,
    performTime = null,
    categories = categories,
    tags = tags,
    tools = emptyList(),
    rating = rating,
    sourceUrl = null,
    dateAdded = null,
    lastMade = lastMade,
)

fun organizer(id: String, name: String) = Organizer(id = id, name = name, slug = id)

fun line(text: String, food: String? = null) = RecipeIngredient(
    referenceId = null,
    display = text,
    quantity = null,
    unit = null,
    food = food?.let { IngredientFood(null, it, null) },
    note = text,
    sectionTitle = null,
)

fun candidate(id: String, vararg ingredients: String, quality: Double = 0.6) =
    PlanCandidate(summary(id), ingredients.toSet(), quality)
