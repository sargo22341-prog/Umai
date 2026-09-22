package org.opensources.umai.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ShoppingListSummaryDto(
    val id: String = "",
    val name: String? = null,
    val groupId: String? = null,
    val userId: String? = null,
    val householdId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val recipeReferences: List<ShoppingListRecipeRefDto> = emptyList(),
)

@Serializable
data class ShoppingListDto(
    val id: String = "",
    val name: String? = null,
    val groupId: String? = null,
    val userId: String? = null,
    val householdId: String? = null,
    val listItems: List<ShoppingListItemDto> = emptyList(),
    val recipeReferences: List<ShoppingListRecipeRefDto> = emptyList(),
)

@Serializable
data class ShoppingListRecipeRefDto(
    val id: String = "",
    val shoppingListId: String = "",
    val recipeId: String = "",
    val recipeQuantity: Double = 1.0,
    val recipe: RecipeSummaryDto? = null,
)

@Serializable
data class ShoppingListItemDto(
    val id: String = "",
    val shoppingListId: String = "",
    val quantity: Double = 1.0,
    val unit: IngredientUnitDto? = null,
    val food: IngredientFoodDto? = null,
    val note: String? = "",
    val display: String = "",
    val checked: Boolean = false,
    val position: Int = 0,
    val foodId: String? = null,
    val labelId: String? = null,
    val unitId: String? = null,
    val label: LabelDto? = null,
    val groupId: String? = null,
    val householdId: String? = null,
)

/**
 * Create/update payloads deliberately carry only the fields Umai edits.
 * Mealie fills the rest server-side; sending back the full `food`/`unit`
 * objects would risk rewriting the shared food database from a phone.
 */
@Serializable
data class ShoppingListItemCreateDto(
    val shoppingListId: String,
    val note: String? = null,
    val quantity: Double = 1.0,
    val checked: Boolean = false,
    val position: Int = 0,
    val foodId: String? = null,
    val unitId: String? = null,
    val labelId: String? = null,
)

@Serializable
data class ShoppingListItemUpdateDto(
    val shoppingListId: String,
    val note: String? = null,
    val quantity: Double = 1.0,
    val checked: Boolean = false,
    val position: Int = 0,
    val foodId: String? = null,
    val unitId: String? = null,
    val labelId: String? = null,
)

@Serializable
data class ShoppingListCreateDto(val name: String)

@Serializable
data class ShoppingListAddRecipeDto(val recipeIncrementQuantity: Double = 1.0)

@Serializable
data class ShoppingListItemsCollectionDto(
    val createdItems: List<ShoppingListItemDto> = emptyList(),
    val updatedItems: List<ShoppingListItemDto> = emptyList(),
    val deletedItems: List<ShoppingListItemDto> = emptyList(),
)
