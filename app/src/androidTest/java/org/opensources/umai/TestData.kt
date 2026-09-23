package org.opensources.umai

import org.opensources.umai.core.model.HouseholdPreferences
import org.opensources.umai.core.model.HouseholdStatistics
import org.opensources.umai.core.model.IngredientFood
import org.opensources.umai.core.model.IngredientUnit
import org.opensources.umai.core.model.MealPlanEntry
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.model.Organizer
import org.opensources.umai.core.model.Recipe
import org.opensources.umai.core.model.RecipeComment
import org.opensources.umai.core.model.RecipeIngredient
import org.opensources.umai.core.model.RecipeStep
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.model.ShoppingItem
import org.opensources.umai.core.model.ShoppingList
import org.opensources.umai.core.model.ShoppingListSummary
import org.opensources.umai.core.model.UserProfile
import org.opensources.umai.recipe.domain.RecipeDraft
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

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
        photo: String? = null,
    ) = RecipeStep(
        id = id,
        title = title,
        text = text,
        images = images,
        ingredientReferenceIds = ingredientRefs,
        photo = photo,
    )

    fun ingredient(
        referenceId: String? = "ref-1",
        display: String = "2 citrons",
        quantity: Double? = null,
        unit: IngredientUnit? = null,
        food: IngredientFood? = null,
        note: String? = null,
    ) = RecipeIngredient(
        referenceId = referenceId,
        display = display,
        quantity = quantity,
        unit = unit,
        food = food,
        note = note,
        sectionTitle = null,
    )

    fun food(name: String = "citron", pluralName: String? = "citrons") =
        IngredientFood(id = "food-1", name = name, pluralName = pluralName)

    fun unit(
        name: String = "gramme",
        pluralName: String? = "grammes",
        abbreviation: String = "g",
        useAbbreviation: Boolean = false,
    ) = IngredientUnit(
        id = "unit-1",
        name = name,
        pluralName = pluralName,
        abbreviation = abbreviation,
        pluralAbbreviation = null,
        useAbbreviation = useAbbreviation,
        fraction = true,
    )

    fun recipe(
        summary: RecipeSummary = summary(),
        ingredients: List<RecipeIngredient> = listOf(ingredient()),
        steps: List<RecipeStep> = listOf(step()),
        commentsDisabled: Boolean = false,
    ) = Recipe(
        summary = summary,
        ingredients = ingredients,
        steps = steps,
        nutrition = null,
        notes = emptyList(),
        showNutrition = false,
        showAssets = false,
        assets = emptyList(),
        commentsDisabled = commentsDisabled,
    )

    fun comment(
        id: String = "c1",
        text: String = "Trop bon",
        authorId: String = "u1",
        authorName: String = "Hiroo",
    ) = RecipeComment(
        id = id,
        recipeId = "r1",
        text = text,
        authorId = authorId,
        authorName = authorName,
        createdAt = OffsetDateTime.of(2026, 2, 2, 10, 0, 0, 0, ZoneOffset.UTC),
    )

    fun user(
        fullName: String = "Hiroo",
        username: String = "hiroo",
        email: String = "hiroo@example.org",
        canManageHousehold: Boolean = true,
        isAdmin: Boolean = false,
    ) = UserProfile(
        id = "u1",
        username = username,
        fullName = fullName,
        email = email,
        isAdmin = isAdmin,
        groupName = "Famille",
        householdName = "Maison",
        cacheKey = "abc",
        canManageHousehold = canManageHousehold,
    )

    fun statistics() = HouseholdStatistics(
        recipes = 114,
        users = 2,
        categories = 17,
        tags = 499,
        tools = 1,
    )

    /** Mealie numbers the days from Sunday: 1 really is Monday. */
    fun householdPreferences(firstDayOfWeek: Int = 1) = HouseholdPreferences(
        firstDayOfWeek = firstDayOfWeek,
        privateHousehold = true,
        showAnnouncements = true,
        lockRecipeEditsFromOtherHouseholds = true,
        recipePublic = true,
        recipeShowNutrition = false,
        recipeShowAssets = false,
        recipeLandscapeView = false,
        recipeDisableComments = false,
    )

    fun draft(
        id: String = "d1",
        name: String = "Tarte aux pommes",
        updatedAt: Long = 1_770_000_000_000L,
    ) = RecipeDraft(id = id, name = name, updatedAt = updatedAt)

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
