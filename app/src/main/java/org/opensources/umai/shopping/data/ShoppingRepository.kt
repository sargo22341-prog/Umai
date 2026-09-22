package org.opensources.umai.shopping.data

import org.opensources.umai.core.model.LinkedRecipe
import org.opensources.umai.core.model.ShoppingItem
import org.opensources.umai.core.model.ShoppingList
import org.opensources.umai.core.model.ShoppingListSummary
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.apiCall
import org.opensources.umai.core.network.dto.ShoppingListAddRecipeDto
import org.opensources.umai.core.network.dto.ShoppingListCreateDto
import org.opensources.umai.core.network.dto.ShoppingListDto
import org.opensources.umai.core.network.dto.ShoppingListItemCreateDto
import org.opensources.umai.core.network.dto.ShoppingListItemDto
import org.opensources.umai.core.network.dto.ShoppingListItemUpdateDto
import org.opensources.umai.core.network.dto.ShoppingListSummaryDto
import org.opensources.umai.core.network.api.MealieApi

/**
 * Shopping lists backed by `/api/households/shopping/...`.
 *
 * Updates send back only the fields Umai edits; the food and unit catalogues
 * stay under Mealie's control.
 */
class ShoppingRepository(private val apiProvider: () -> MealieApi?) {

    suspend fun lists(): ApiResult<List<ShoppingListSummary>> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return apiCall { api.shoppingLists().items.map { it.toDomain() } }
    }

    suspend fun list(id: String): ApiResult<ShoppingList> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return apiCall { api.shoppingList(id).toDomain() }
    }

    suspend fun createList(name: String): ApiResult<ShoppingList> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return apiCall { api.createShoppingList(ShoppingListCreateDto(name)).toDomain() }
    }

    suspend fun deleteList(id: String): ApiResult<Unit> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return apiCall { api.deleteShoppingList(id) }
    }

    suspend fun addItem(listId: String, note: String, quantity: Double, position: Int): ApiResult<Unit> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        // The created item is re-read with the list, so the response is dropped.
        return apiCall {
            api.createShoppingItem(
                ShoppingListItemCreateDto(
                    shoppingListId = listId,
                    note = note,
                    quantity = quantity,
                    position = position,
                ),
            ).let { }
        }
    }

    suspend fun updateItem(item: ShoppingItem): ApiResult<Unit> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        // Mealie answers with the created/updated/deleted collection; the list
        // is reloaded right after, so the response is dropped.
        return apiCall {
            api.updateShoppingItem(
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
            ).let { }
        }
    }

    suspend fun deleteItem(itemId: String): ApiResult<Unit> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return apiCall { api.deleteShoppingItem(itemId) }
    }

    /** Uses Mealie's own "add recipe ingredients to list" endpoint. */
    suspend fun addRecipe(listId: String, recipeId: String, servings: Double): ApiResult<ShoppingList> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return apiCall {
            api.addRecipeToShoppingList(
                listId = listId,
                recipeId = recipeId,
                body = ShoppingListAddRecipeDto(recipeIncrementQuantity = servings),
            ).toDomain()
        }
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
