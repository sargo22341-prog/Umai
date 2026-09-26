package org.opensources.umai.planning.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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
 * until [forget], or until the app is signed in somewhere else.
 */
class RecipeCaloriesRepository(
    private val apiProvider: () -> MealieApi?,
    private val instanceKey: () -> String?,
) {

    /**
     * Held while recipes are asked for: a week reloaded meanwhile waits, then
     * finds their answers here instead of asking again.
     */
    private val lock = Mutex()

    /** Recipe id to its calories, `null` for a recipe that has none. [forget] may clear it from any thread. */
    private val fetched: MutableMap<String, Int?> = Collections.synchronizedMap(mutableMapOf())

    /** The instance [fetched] was read from; guarded by [lock]. */
    private var fetchedFor: String? = null

    /**
     * The calories of each of [recipes], by recipe id; `null` when the recipe
     * has none, or when Mealie could not be asked (asked again next time).
     */
    suspend fun calories(recipes: Collection<RecipeSummary>): Map<String, Int?> = lock.withLock {
        val key = instanceKey()
        if (key != fetchedFor) {
            fetched.clear()
            fetchedFor = key
        }
        val distinct = recipes.distinctBy { it.id }
        val tagged = distinct.associate { it.id to PlanCalories.ofTags(it) }
        val missing = distinct.filter { tagged[it.id] == null && it.id !in fetched }
        val api = apiProvider()
        if (api != null && missing.isNotEmpty()) {
            val requests = Semaphore(MAX_PARALLEL_REQUESTS)
            val answers = coroutineScope {
                missing.map { recipe ->
                    async { recipe.id to requests.withPermit { apiCall { api.recipe(recipe.slug) } } }
                }.awaitAll()
            }
            answers.forEach { (id, result) ->
                if (result is ApiResult.Success) fetched[id] = CalorieTags.parse(result.value.nutrition?.calories)
            }
        }
        distinct.associate { it.id to (tagged[it.id] ?: fetched[it.id]) }
    }

    /** Forgets the nutrition read from Mealie, which may have changed since. */
    fun forget() = fetched.clear()

    private companion object {
        /** A few recipes read at once: a week of new recipes never floods the instance. */
        const val MAX_PARALLEL_REQUESTS = 4
    }
}
