package org.opensources.umai.recipe.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.api.MealieApi
import org.opensources.umai.core.network.call
import org.opensources.umai.core.network.dto.IngredientNameDto
import org.opensources.umai.core.network.valueOr
import org.opensources.umai.recipe.domain.IngredientLineParser

/** Resolve manually entered names to explicit catalog identities before updating the recipe. */
internal class IngredientCatalogWriter(private val api: MealieApi) {
    suspend fun resolve(document: JsonObject): ApiResult<JsonObject> {
        val lines = document["recipeIngredient"] as? JsonArray ?: return ApiResult.Success(document)
        if (lines.size > 1000) return ApiResult.Failure(NetworkError.InvalidResponse)
        val resolved = mutableListOf<JsonObject>()
        val known = mutableMapOf<Pair<Boolean, String>, JsonObject>()
        for (element in lines) {
            var line = element as? JsonObject ?: return ApiResult.Failure(NetworkError.InvalidResponse)
            for (field in listOf("food", "unit")) {
                val choice = line[field] as? JsonObject ?: continue
                if (!choice["id"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) continue
                val name = choice["name"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?.takeIf { it.isNotBlank() } ?: return ApiResult.Failure(NetworkError.InvalidResponse)
                val key = (field == "food") to IngredientLineParser.normalize(name)
                val value = known[key] ?: resolveName(name, key.first).valueOr { return it }.also { known[key] = it }
                line = JsonObject(line + (field to value))
            }
            resolved.add(line)
        }
        return ApiResult.Success(JsonObject(document + ("recipeIngredient" to JsonArray(resolved))))
    }

    private suspend fun resolveName(name: String, food: Boolean): ApiResult<JsonObject> {
        val provider = { api }
        if (food) {
            val matches = provider.call { foods(search = name, perPage = 100) }.valueOr { return it }
            val existing = matches.items.firstOrNull { IngredientLineParser.normalize(it.name) == IngredientLineParser.normalize(name) }
            // A partial catalog cannot prove that a name is new.
            if (existing == null && matches.total > matches.items.size) return ApiResult.Failure(NetworkError.InvalidResponse)
            val found = existing ?: provider.call { createIngredientFood(IngredientNameDto(name)) }.valueOr { return it }
            return identity(found.id, found.name)
        }
        val canonical = IngredientLineParser.canonicalUnit(name)
        val matches = provider.call { units(search = if (canonical == null) name else null, perPage = 200) }.valueOr { return it }
        val existing = matches.items.firstOrNull { unit ->
            IngredientLineParser.normalize(unit.name) == IngredientLineParser.normalize(name) ||
                (canonical != null && listOfNotNull(unit.name, unit.pluralName, unit.abbreviation, unit.pluralAbbreviation)
                    .any { IngredientLineParser.canonicalUnit(it) == canonical })
        }
        if (existing == null && matches.total > matches.items.size) return ApiResult.Failure(NetworkError.InvalidResponse)
        val found = existing ?: provider.call { createIngredientUnit(IngredientNameDto(name)) }.valueOr { return it }
        return identity(found.id, found.name)
    }

    private fun identity(id: String?, name: String): ApiResult<JsonObject> {
        if (id.isNullOrBlank()) return ApiResult.Failure(NetworkError.InvalidResponse)
        return ApiResult.Success(JsonObject(mapOf("id" to JsonPrimitive(id), "name" to JsonPrimitive(name))))
    }
}
