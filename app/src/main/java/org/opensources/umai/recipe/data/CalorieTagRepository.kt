package org.opensources.umai.recipe.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.opensources.umai.core.model.AllPages
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.MealieClientFactory
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.api.MealieApi
import org.opensources.umai.core.network.apiCall
import org.opensources.umai.core.network.call
import org.opensources.umai.core.network.fetchAllPages
import org.opensources.umai.core.network.map
import org.opensources.umai.core.network.orInvalid
import org.opensources.umai.core.network.toPaged
import org.opensources.umai.core.network.valueOr
import org.opensources.umai.core.network.dto.RecipeDetailDto
import org.opensources.umai.core.network.dto.RecipeTagDto
import org.opensources.umai.core.network.dto.TagInDto
import org.opensources.umai.core.session.InstanceCache
import org.opensources.umai.recipe.domain.CalorieTag
import org.opensources.umai.recipe.domain.CalorieTags

/**
 * Keeps the `calorie-<value>` tag of recipes in step with their nutrition
 * (see [CalorieTags]), and lists the calorie tags the search filters on.
 */
class CalorieTagRepository(
    private val apiProvider: () -> MealieApi?,
    instanceKey: () -> String?,
) {

    private val cache = InstanceCache<List<CalorieTag>>(instanceKey)

    /** Every calorie tag of the instance, kept until one is created here. */
    suspend fun calorieTags(): ApiResult<List<CalorieTag>> = cache.get {
        apiProvider.call {
            fetchAllPages(MAX_PAGES) { page ->
                tags(page = page, perPage = PAGE_SIZE, search = CalorieTags.PREFIX).toPaged { it.toCalorieTag() }
            }.items
        }
    }

    /**
     * The slugs of every recipe of the instance, for [sync] to go through; not
     * complete past [MAX_RECIPE_PAGES] pages of them.
     */
    suspend fun recipeSlugs(): ApiResult<AllPages<String>> = apiProvider.call {
        fetchAllPages(MAX_RECIPE_PAGES) { page ->
            recipes(page = page, perPage = RECIPE_PAGE_SIZE, orderBy = "name", orderDirection = "asc")
                .toPaged { it.slug.takeIf(String::isNotBlank) }
        }
    }

    /**
     * Gives the recipe the tag of its calories, or removes its calorie tags when
     * it has none. Nothing is written when the tag is already right, and only
     * the tags are: a change made meanwhile to the rest of the recipe is kept.
     * Answers whether the recipe changed.
     */
    suspend fun sync(slug: String): ApiResult<Boolean> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        val (document, detail) = apiCall {
            val document = api.recipeDocument(slug)
            document to MealieClientFactory.json.decodeFromJsonElement(RecipeDetailDto.serializer(), document)
        }.valueOr { return it }
        val wanted = CalorieTags.parse(detail.nutrition?.calories)?.let(CalorieTags::tagName)
        val calorieTags = detail.tags.orEmpty().filter { CalorieTags.isCalorieTag(it.slug) }
        if (calorieTags.map { it.slug } == listOfNotNull(wanted)) return ApiResult.Success(false)

        val tag = wanted?.let { findOrCreate(api, it).valueOr { failure -> return failure } }
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
        return apiCall { api.patchRecipe(slug, buildJsonObject { put("tags", tags) }) }.map { true }
    }

    private suspend fun findOrCreate(api: MealieApi, name: String): ApiResult<RecipeTagDto> {
        val existing = apiCall { api.tags(perPage = PAGE_SIZE, search = name) }.valueOr { return it }
            .items.firstOrNull { it.slug == name && it.id != null }
        if (existing != null) return ApiResult.Success(existing)
        return apiCall { api.createTag(TagInDto(name)) }
            .also { if (it is ApiResult.Success) cache.clear() }
            .map { created -> created.takeIf { it.id != null } }
            .orInvalid()
    }

    private fun RecipeTagDto.toCalorieTag(): CalorieTag? {
        val identifier = id ?: return null
        val calories = CalorieTags.valueOf(slug) ?: return null
        return CalorieTag(identifier, slug, calories)
    }

    private companion object {
        const val PAGE_SIZE = 200
        const val MAX_PAGES = 20
        const val RECIPE_PAGE_SIZE = 100

        /** 10 000 recipes: the sync of a larger instance says it stopped there. */
        const val MAX_RECIPE_PAGES = 100
    }
}
