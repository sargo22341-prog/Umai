package org.opensources.umai.shopping.data

import org.opensources.umai.core.model.LinkedRecipe
import org.opensources.umai.core.model.RecipeIngredient
import org.opensources.umai.core.model.ShoppingItem
import org.opensources.umai.core.model.ShoppingList
import org.opensources.umai.core.model.ShoppingListSummary
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.api.MealieApi
import org.opensources.umai.core.network.call
import org.opensources.umai.core.network.dto.ShoppingListAddRecipeDto
import org.opensources.umai.core.network.dto.ShoppingListCreateDto
import org.opensources.umai.core.network.dto.ShoppingListDto
import org.opensources.umai.core.network.dto.ShoppingListItemCreateDto
import org.opensources.umai.core.network.dto.ShoppingListItemDto
import org.opensources.umai.core.network.dto.ShoppingListItemUpdateDto
import org.opensources.umai.core.network.dto.ShoppingListSummaryDto
import org.opensources.umai.recipe.data.toDto

/**
 * Shopping lists backed by `/api/households/shopping/...`.
 *
 * Updates send back only the fields Umai edits; the food and unit catalogues
 * stay under Mealie's control.
 */
class ShoppingRepository(private val apiProvider: () -> MealieApi?) {

    suspend fun lists(): ApiResult<List<ShoppingListSummary>> =
        apiProvider.call { shoppingLists().items.map { it.toDomain() } }

    suspend fun list(id: String): ApiResult<ShoppingList> = apiProvider.call { shoppingList(id).toDomain() }

    suspend fun createList(name: String): ApiResult<ShoppingList> =
        apiProvider.call { createShoppingList(ShoppingListCreateDto(name)).toDomain() }

    suspend fun deleteList(id: String): ApiResult<Unit> = apiProvider.call { deleteShoppingList(id) }

    suspend fun addItem(listId: String, note: String, quantity: Double, position: Int): ApiResult<Unit> =
        apiProvider.call {
            createShoppingItem(
                ShoppingListItemCreateDto(
                    shoppingListId = listId,
                    note = note,
                    quantity = quantity,
                    position = position,
                ),
            )
        }

    suspend fun updateItem(item: ShoppingItem): ApiResult<Unit> = apiProvider.call {
        updateShoppingItem(
            id = item.id,
            body = ShoppingListItemUpdateDto(
                shoppingListId = item.shoppingListId,
                note = item.note,
                quantity = item.quantity,
                checked = item.checked,
                position = item.position,
                foodId = item.foodId,
                unitId = item.unitId,
                labelId = item.labelId,
            ),
        )
    }

    suspend fun deleteItem(itemId: String): ApiResult<Unit> = apiProvider.call { deleteShoppingItem(itemId) }

    /**
     * Uses Mealie's own "add recipe ingredients to list" endpoint.
     *
     * [multiplier] is what Mealie calls `recipeIncrementQuantity`: it scales
     * every quantity, so asking for six servings of a four-serving recipe sends
     * 1.5. Passing [ingredients] restricts the transfer to the lines the user
     * kept ticked; `null` sends the whole recipe.
     */
    suspend fun addRecipe(
        listId: String,
        recipeId: String,
        multiplier: Double,
        ingredients: List<RecipeIngredient>? = null,
    ): ApiResult<ShoppingList> = apiProvider.call {
        addRecipeToShoppingList(
            listId = listId,
            recipeId = recipeId,
            body = ShoppingListAddRecipeDto(
                recipeIncrementQuantity = multiplier.takeIf { it > 0.0 } ?: 1.0,
                recipeIngredients = ingredients?.map { it.toDto() },
            ),
        ).toDomain()
    }
}

private fun ShoppingListSummaryDto.toDomain() = ShoppingListSummary(
    id = id,
    name = name.orEmpty(),
    recipeCount = recipeReferences.size,
)

private fun ShoppingListDto.toDomain() = ShoppingList(
    id = id,
    name = name.orEmpty(),
    items = listItems.map { it.toDomain() }.sortedBy { it.position },
    linkedRecipes = recipeReferences.map {
        LinkedRecipe(
            recipeId = it.recipeId,
            name = it.recipe?.name.orEmpty(),
            quantity = it.recipeQuantity,
        )
    },
)

private fun ShoppingListItemDto.toDomain() = ShoppingItem(
    id = id,
    shoppingListId = shoppingListId,
    display = display,
    note = note,
    quantity = quantity,
    checked = checked,
    position = position,
    foodId = foodId ?: food?.id,
    unitId = unitId ?: unit?.id,
    labelId = labelId ?: label?.id,
    labelName = label?.name,
    labelColor = label?.color,
)
