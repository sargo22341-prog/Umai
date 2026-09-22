package org.opensources.umai.core.model

/** A recipe as listed by `GET /api/recipes`. */
data class RecipeSummary(
    val id: String,
    val slug: String,
    val name: String,
    val description: String,
    /** Cache-busting token from Mealie; `null` when the recipe has no picture. */
    val imageToken: String?,
    val servings: Double,
    val yieldText: String?,
    val totalTime: String?,
    val prepTime: String?,
    val cookTime: String?,
    val performTime: String?,
    val categories: List<Organizer>,
    val tags: List<Organizer>,
    val tools: List<Organizer>,
    val rating: Double?,
    val sourceUrl: String?,
    val dateAdded: String?,
    val lastMade: String?,
) {
    val hasImage: Boolean get() = imageToken != null
}

/** Full recipe as returned by `GET /api/recipes/{slug}`. */
data class Recipe(
    val summary: RecipeSummary,
    val ingredients: List<RecipeIngredient>,
    val steps: List<RecipeStep>,
    val nutrition: Nutrition?,
    val notes: List<RecipeNote>,
    val showNutrition: Boolean,
    val showAssets: Boolean,
    val assets: List<RecipeAsset>,
) {
    val id: String get() = summary.id
    val slug: String get() = summary.slug
    val name: String get() = summary.name
}

/**
 * One instruction. Mealie has no dedicated image field on a step: pictures are
 * embedded in the step text as Markdown or HTML pointing at recipe assets, so
 * they are extracted here and rendered as real images by the cooking mode.
 */
data class RecipeStep(
    val id: String,
    val title: String?,
    val text: String,
    val images: List<String>,
    val ingredientReferenceIds: List<String>,
)

data class RecipeIngredient(
    val referenceId: String?,
    /** Pre-rendered text from Mealie; always safe to show as-is. */
    val display: String,
    val quantity: Double?,
    val unit: String?,
    val food: String?,
    val note: String?,
    /** Section header introduced by Mealie when an ingredient carries a title. */
    val sectionTitle: String?,
    val foodId: String?,
    val unitId: String?,
)

data class RecipeNote(val title: String, val text: String)

data class RecipeAsset(val name: String, val icon: String, val fileName: String?)

data class Nutrition(
    val calories: String?,
    val carbohydrates: String?,
    val fat: String?,
    val protein: String?,
    val fiber: String?,
    val sugar: String?,
    val sodium: String?,
    val cholesterol: String?,
    val saturatedFat: String?,
    val transFat: String?,
    val unsaturatedFat: String?,
) {
    val isEmpty: Boolean
        get() = listOf(
            calories, carbohydrates, fat, protein, fiber,
            sugar, sodium, cholesterol, saturatedFat, transFat, unsaturatedFat,
        ).all { it.isNullOrBlank() }
}

/** Shared shape of categories, tags and tools. */
data class Organizer(
    val id: String,
    val name: String,
    val slug: String,
    val recipeCount: Int = 0,
)

/** Food entries used by the ingredient filter and the shopping list. */
data class Food(
    val id: String,
    val name: String,
    val labelName: String?,
    val labelColor: String?,
)

data class Label(val id: String, val name: String, val color: String)

/** One page of a paginated Mealie collection. */
data class Paged<T>(
    val items: List<T>,
    val page: Int,
    val totalPages: Int,
    val total: Int,
) {
    val hasNext: Boolean get() = page < totalPages
}

/**
 * Accumulated pages of a paginated collection, as shown by an infinite list.
 * Screens append to it instead of each re-implementing page bookkeeping.
 */
data class PagedItems<T>(
    val items: List<T> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 0,
    val total: Int = 0,
) {
    val canLoadMore: Boolean get() = page in 1..<totalPages
    val isInitial: Boolean get() = page == 0

    fun append(next: Paged<T>): PagedItems<T> = PagedItems(
        items = if (next.page <= 1) next.items else items + next.items,
        page = next.page,
        totalPages = next.totalPages,
        total = next.total,
    )
}
