package org.opensources.umai

import org.opensources.umai.core.model.MealPlanEntry
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.model.Organizer
import org.opensources.umai.core.model.Recipe
import org.opensources.umai.core.model.RecipeIngredient
import org.opensources.umai.core.model.RecipeStep
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.model.ShoppingItem
import org.opensources.umai.core.model.ShoppingList
import org.opensources.umai.core.model.ShoppingListSummary
import java.time.LocalDate

/** Fixtures shared by the UI tests. */
object TestData {

    fun summary(
        id: String = "r1",
        name: String = "Poulet au curry",
        slug: String = "poulet-au-curry",
        imageToken: String? = "73",
        servings: Double = 2.0,
        totalTime: String? = "15 minutes",
        rating: Double? = null,
        tags: List<Organizer> = emptyList(),
    ) = RecipeSummary(
        id = id,
        slug = slug,
        name = name,
        description = "Un curry tout doux.",
        imageToken = imageToken,
        servings = servings,
        yieldText = null,
        totalTime = totalTime,
        prepTime = null,
        cookTime = null,
        performTime = null,
        categories = emptyList(),
        tags = tags,
        tools = emptyList(),
        rating = rating,
        sourceUrl = null,
        dateAdded = null,
        lastMade = null,
    )

    fun step(
        id: String = "s1",
        title: String? = null,
        text: String = "Cuire le riz.",
        images: List<String> = emptyList(),
        ingredientRefs: List<String> = emptyList(),
    ) = RecipeStep(
        id = id,
        title = title,
        text = text,
        images = images,
        ingredientReferenceIds = ingredientRefs,
    )

    fun ingredient(referenceId: String? = "ref-1", display: String = "2 citrons") = RecipeIngredient(
        referenceId = referenceId,
        display = display,
        quantity = null,
        unit = null,
        food = null,
        note = null,
        sectionTitle = null,
        foodId = null,
        unitId = null,
    )

    fun recipe(
        summary: RecipeSummary = summary(),
        ingredients: List<RecipeIngredient> = listOf(ingredient()),
        steps: List<RecipeStep> = listOf(step()),
    ) = Recipe(
        summary = summary,
        ingredients = ingredients,
        steps = steps,
        nutrition = null,
        notes = emptyList(),
        showNutrition = false,
        showAssets = false,
        assets = emptyList(),
    )

    fun shoppingListSummary(id: String = "l1", name: String = "Cellier") =
        ShoppingListSummary(id = id, name = name, recipeCount = 0)

    fun shoppingItem(
        id: String = "i1",
        display: String = "2 citrons",
        checked: Boolean = false,
        position: Int = 0,
        labelName: String? = null,
    ) = ShoppingItem(
        id = id,
        shoppingListId = "l1",
        display = display,
        note = display,
        quantity = 1.0,
        checked = checked,
        position = position,
        foodId = null,
        unitId = null,
        labelId = null,
        labelName = labelName,
        labelColor = null,
    )

    fun shoppingList(items: List<ShoppingItem> = listOf(shoppingItem())) = ShoppingList(
        id = "l1",
        name = "Cellier",
        items = items,
        linkedRecipes = emptyList(),
    )

    fun planEntry(
        id: Int = 1,
        date: LocalDate = LocalDate.now(),
        type: MealType = MealType.DINNER,
        recipe: RecipeSummary? = summary(),
        title: String = "",
    ) = MealPlanEntry(
        id = id,
        date = date,
        type = type,
        title = title,
        text = "",
        recipe = recipe,
        groupId = "g",
        userId = "u",
    )
}
