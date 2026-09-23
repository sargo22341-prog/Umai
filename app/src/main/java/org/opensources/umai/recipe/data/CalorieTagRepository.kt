package org.opensources.umai.recipe.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.MealieClientFactory
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.api.MealieApi
import org.opensources.umai.core.network.apiCall
import org.opensources.umai.core.network.dto.RecipeDetailDto
import org.opensources.umai.core.network.dto.RecipeTagDto
import org.opensources.umai.core.network.dto.TagInDto
import org.opensources.umai.recipe.domain.CalorieTag
import org.opensources.umai.recipe.domain.CalorieTags

/**
 * Keeps the `calorie-<value>` tag of recipes in step with their nutrition
 * (see [CalorieTags]), and lists the calorie tags the search filters on.
 */
class CalorieTagRepository(private val apiProvider: () -> MealieApi?) {

    private var cached: List<CalorieTag>? = null

    /** Every calorie tag of the instance, kept until one is created here. */
    suspend fun calorieTags(): ApiResult<List<CalorieTag>> {
        cached?.let { return ApiResult.Success(it) }
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        return apiCall {
            val all = mutableListOf<CalorieTag>()
            var page = 1
            do {
                val result = api.tags(page = page, perPage = PAGE_SIZE, search = CalorieTags.PREFIX)
                all += result.items.mapNotNull { it.toCalorieTag() }
                page++
            } while (page <= result.totalPages && page <= MAX_PAGES)
            all.toList()
        }.also { if (it is ApiResult.Success) cached = it.value }
    }

    /**
     * Gives the recipe the tag of its calories, or removes its calorie tags when
     * it has none. Nothing is written when the tag is already right. Answers
     * whether the recipe changed.
     */
    suspend fun sync(slug: String): ApiResult<Boolean> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        val document = when (val result = apiCall { api.recipeDocument(slug) }) {
            is ApiResult.Failure -> return result
            is ApiResult.Success -> result.value
        }
        val detail = MealieClientFactory.json.decodeFromJsonElement(RecipeDetailDto.serializer(), document)
        val calories = CalorieTags.parse(detail.nutrition?.calories)
        val current = detail.tags.orEmpty()
        val wanted = calories?.let(CalorieTags::tagName)
        val calorieTags = current.filter { CalorieTags.isCalorieTag(it.slug) }
        if (calorieTags.map { it.slug } == listOfNotNull(wanted)) return ApiResult.Success(false)

        val tag = if (wanted == null) {
            null
        } else {
            when (val result = findOrCreate(api, wanted)) {
                is ApiResult.Failure -> return result
                is ApiResult.Success -> result.value
            }
        }
        val kept = (document["tags"] as? JsonArray).orEmpty().filterNot { element ->
            val slug = (element as? JsonObject)?.get("slug")?.jsonPrimitive?.contentOrNull.orEmpty()
            CalorieTags.isCalorieTag(slug)
        }
        val tags = JsonArray(
            kept + listOfNotNull(
                tag?.let {
                    buildJsonObject {
                        put("id", it.id)
                        put("name", it.name)
                        put("slug", it.slug)
                    }
                },
            ),
        )
        return apiCall { api.replaceRecipe(slug, JsonObject(document + ("tags" to tags))) }
            .let { if (it is ApiResult.Failure) it else ApiResult.Success(true) }
    }

    private suspend fun findOrCreate(api: MealieApi, name: String): ApiResult<RecipeTagDto> {
        val existing = when (val result = apiCall { api.tags(perPage = PAGE_SIZE, search = name) }) {
            is ApiResult.Failure -> return result
            is ApiResult.Success -> result.value.items.firstOrNull { it.slug == name && it.id != null }
        }
        if (existing != null) return ApiResult.Success(existing)
        return when (val created = apiCall { api.createTag(TagInDto(name)) }) {
            is ApiResult.Failure -> created
            is ApiResult.Success -> {
                cached = null
                created.value.takeIf { it.id != null }?.let { ApiResult.Success(it) }
                    ?: ApiResult.Failure(NetworkError.InvalidResponse)
            }
        }
    }

    private fun RecipeTagDto.toCalorieTag(): CalorieTag? {
        val identifier = id ?: return null
        val calories = CalorieTags.valueOf(slug) ?: return null
        return CalorieTag(identifier, slug, calories)
    }

    private companion object {
        const val PAGE_SIZE = 200
        const val MAX_PAGES = 20
    }
}

private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
