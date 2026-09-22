package org.opensources.umai.core.model

data class ShoppingListSummary(
    val id: String,
    val name: String,
    val recipeCount: Int,
)

data class ShoppingList(
    val id: String,
    val name: String,
    val items: List<ShoppingItem>,
    val linkedRecipes: List<LinkedRecipe>,
) {
    val checkedCount: Int get() = items.count { it.checked }
}

data class LinkedRecipe(
    val recipeId: String,
    val name: String,
    val quantity: Double,
)

data class ShoppingItem(
    val id: String,
    val shoppingListId: String,
    /** Mealie renders `quantity + unit + food + note` into `display`. */
    val display: String,
    val note: String?,
    val quantity: Double,
    val checked: Boolean,
    val position: Int,
    val foodId: String?,
    val unitId: String?,
    val labelId: String?,
    val labelName: String?,
    val labelColor: String?,
) {
    /** What to show when Mealie has not pre-rendered a display string. */
    val label: String get() = display.ifBlank { note.orEmpty() }
}
