package org.opensources.umai.planning.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.api.MealieApi
import org.opensources.umai.core.network.apiCall
import org.opensources.umai.planning.domain.PlanCalories
import org.opensources.umai.recipe.domain.CalorieTags
import java.util.Collections

/**
 * The calories of one serving of the recipes of the plan.
 *
 * The plan lists its recipes without their nutrition, but with their tags: a
 * recipe written or imported with Umai carries its calories in a
 * `calorie-<value>` tag ([CalorieTags]), read at no cost. Only a recipe without
 * that tag has its nutrition asked of Mealie, once: what it answers is kept
 * until [forget].
 */
class RecipeCaloriesRepository(private val apiProvider: () -> MealieApi?) {

    /**
     * Recipe id to its calories, `null` for a recipe that has none. A week
     * reloaded while its recipes are asked for writes here from two calls.
     */
    private val fetched: MutableMap<String, Int?> = Collections.synchronizedMap(mutableMapOf())

    /**
     * The calories of each of [recipes], by recipe id; `null` when the recipe
     * has none, or when Mealie could not be asked (asked again next time).
     */
    suspend fun calories(recipes: Collection<RecipeSummary>): Map<String, Int?> {
        val distinct = recipes.distinctBy { it.id }
        val tagged = distinct.associate { it.id to PlanCalories.ofTags(it) }
        val missing = distinct.filter { tagged[it.id] == null && it.id !in fetched }
        val api = apiProvider()
        if (api != null && missing.isNotEmpty()) {
            val answers = coroutineScope {
                missing.map { recipe -> async { recipe.id to apiCall { api.recipe(recipe.slug) } } }.awaitAll()
            }
            answers.forEach { (id, result) ->
                if (result is ApiResult.Success) fetched[id] = CalorieTags.parse(result.value.nutrition?.calories)
            }
        }
        return distinct.associate { it.id to (tagged[it.id] ?: fetched[it.id]) }
    }

    /** Forgets the nutrition read from Mealie, which may have changed since. */
    fun forget() = fetched.clear()
}
