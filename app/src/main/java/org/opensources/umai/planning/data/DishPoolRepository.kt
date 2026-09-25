package org.opensources.umai.planning.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.opensources.umai.core.format.ApiDates
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.model.Recipe
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.api.MealieApi
import org.opensources.umai.core.network.apiCall
import org.opensources.umai.planning.domain.CourseClassifier
import org.opensources.umai.planning.domain.CourseVocabulary
import org.opensources.umai.planning.domain.DishCourse
import org.opensources.umai.planning.domain.IngredientKeys
import org.opensources.umai.planning.domain.MealPlanner
import org.opensources.umai.planning.domain.ModelCourseClassifier
import org.opensources.umai.planning.domain.PlanCandidate
import org.opensources.umai.planning.domain.PlanRule
import org.opensources.umai.planning.domain.UnplacedRecipe
import org.opensources.umai.recipe.data.toDomain
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

/** Where the gathering of dishes stands. */
enum class DishPoolPhase { READING_RECIPES, READING_DISHES, RECOGNIZING }

/** The dishes a plan can be made of, and the rules of the household it must follow. */
data class DishPool(val candidates: List<PlanCandidate>, val rules: List<PlanRule>)

/**
 * Gathers, from Mealie, the dishes the automatic planning chooses from:
 *
 * 1. every recipe of the instance, placed as a dish or not ([CourseClassifier])
 *    from its categories, tags and name, and from the meals it was planned in
 *    over the last weeks;
 * 2. a sample of the dishes, favouring the best rated and those not eaten
 *    lately, whose ingredients are read;
 * 3. the recipes still unplaced after that are asked of the local language
 *    model when there is one, and otherwise judged by their ingredients.
 *
 * Mealie's own meal plan rules are read too, with the recipes each allows.
 */
class DishPoolRepository(
    private val apiProvider: () -> MealieApi?,
    private val courses: DishCourses,
    private val modelClassifier: ModelCourseClassifier,
) {

    suspend fun dishPool(
        today: LocalDate,
        random: Random,
        onPhase: (DishPoolPhase) -> Unit,
    ): ApiResult<DishPool> {
        val api = apiProvider() ?: return ApiResult.Failure(NetworkError.Unauthorized)
        onPhase(DishPoolPhase.READING_RECIPES)
        val recipes = when (val result = allRecipes(api, queryFilter = null)) {
            is ApiResult.Failure -> return result
            is ApiResult.Success -> result.value
        }
        val history = when (val result = history(api, today)) {
            is ApiResult.Failure -> return result
            is ApiResult.Success -> result.value
        }
        val rules = rules(api)
        val userCourses = courses.userCourses.first()
        val modelCourses = courses.modelCourses()

        val placed = recipes.associate { recipe ->
            val course = CourseClassifier.classify(
                recipe = recipe,
                organizerCourse = { CourseVocabulary.ofOrganizer(it.name) },
                userCourses = userCourses,
                pastMeals = history.counts[recipe.id].orEmpty(),
            ) ?: modelCourses[recipe.id]
            recipe.id to course
        }
        val eligible = recipes.filter { placed[it.id] == null || placed[it.id] == DishCourse.MAIN }
        val quality = eligible.associate { it.id to MealPlanner.quality(it, today, history.lastPlanned[it.id]) }
        fun weight(recipe: RecipeSummary) =
            quality.getValue(recipe.id) * if (placed[recipe.id] == null) UNPLACED_WEIGHT else 1.0

        // Enough dishes for each rule of the household to find some.
        val sampled = (
            MealPlanner.sample(eligible, SAMPLE_SIZE, random, ::weight) +
                rules.flatMap { rule ->
                    MealPlanner.sample(eligible.filter { it.id in rule.recipeIds }, SAMPLE_PER_RULE, random, ::weight)
                }
            ).distinctBy { it.id }

        onPhase(DishPoolPhase.READING_DISHES)
        val details = when (val result = details(api, sampled)) {
            is ApiResult.Failure -> return result
            is ApiResult.Success -> result.value
        }

        val unplaced = details.filter { placed[it.id] == null }
        val decided = if (unplaced.isEmpty()) {
            emptyMap()
        } else {
            onPhase(DishPoolPhase.RECOGNIZING)
            modelClassifier.classify(
                unplaced.map { recipe ->
                    UnplacedRecipe(recipe.id, recipe.name, recipe.ingredients.mapNotNull(IngredientKeys::keyOf).distinct())
                },
            ).also { courses.rememberModelCourses(it) }
        }

        val candidates = details.mapNotNull { recipe ->
            val keys = IngredientKeys.keysOf(recipe.ingredients)
            val course = placed[recipe.id] ?: decided[recipe.id] ?: byIngredients(keys)
            if (course != DishCourse.MAIN) return@mapNotNull null
            PlanCandidate(recipe.summary, keys, quality.getValue(recipe.id), IngredientKeys.labelsOf(recipe.ingredients))
        }
        return ApiResult.Success(DishPool(candidates, rules))
    }

    /**
     * What the dishes already planned need: the new ones are chosen to use it
     * up. A dish that cannot be read counts for nothing rather than failing.
     */
    suspend fun ingredientsOf(recipes: List<RecipeSummary>): Set<String> {
        val api = apiProvider() ?: return emptySet()
        val read = (details(api, recipes.distinctBy { it.id }) as? ApiResult.Success)?.value.orEmpty()
        return read.flatMap { IngredientKeys.keysOf(it.ingredients) }.toSet()
    }

    /**
     * Without a model, a recipe nothing places is a dish when its ingredients
     * say so: a few of them, and not only sweet ones.
     */
    private fun byIngredients(keys: Set<String>): DishCourse = when {
        keys.size < MIN_DISH_INGREDIENTS -> DishCourse.OTHER
        IngredientKeys.looksSweet(keys) -> DishCourse.DESSERT
        else -> DishCourse.MAIN
    }

    private suspend fun allRecipes(api: MealieApi, queryFilter: String?): ApiResult<List<RecipeSummary>> {
        val recipes = mutableListOf<RecipeSummary>()
        var page = 1
        while (page <= MAX_PAGES) {
            val result = apiCall {
                api.recipes(page = page, perPage = PAGE_SIZE, orderBy = "name", orderDirection = "asc", queryFilter = queryFilter)
            }
            when (result) {
                is ApiResult.Failure -> return result
                is ApiResult.Success -> {
                    recipes += result.value.items.mapNotNull { it.toDomain() }
                    if (page >= result.value.totalPages) break
                }
            }
            page++
        }
        return ApiResult.Success(recipes)
    }

    private class History(val counts: Map<String, Map<MealType, Int>>, val lastPlanned: Map<String, LocalDate>)

    /** How each recipe was planned over the last weeks, and when it last was as a meal. */
    private suspend fun history(api: MealieApi, today: LocalDate): ApiResult<History> {
        val entries = mutableListOf<Triple<String, MealType, LocalDate>>()
        var page = 1
        while (page <= MAX_PAGES) {
            val result = apiCall {
                api.mealPlans(
                    startDate = ApiDates.format(today.minusWeeks(HISTORY_WEEKS)),
                    endDate = ApiDates.format(today.minusDays(1)),
                    page = page,
                )
            }
            when (result) {
                is ApiResult.Failure -> return result
                is ApiResult.Success -> {
                    result.value.items.forEach { entry ->
                        val id = entry.recipeId ?: entry.recipe?.id ?: return@forEach
                        val date = ApiDates.parseDate(entry.date) ?: return@forEach
                        entries += Triple(id, MealType.fromApi(entry.entryType), date)
                    }
                    if (page >= result.value.totalPages) break
                }
            }
            page++
        }
        val counts = entries.groupBy { it.first }.mapValues { (_, meals) -> meals.groupingBy { it.second }.eachCount() }
        val lastPlanned = entries.filter { CourseClassifier.courseOf(it.second) == DishCourse.MAIN }
            .groupBy { it.first }
            .mapValues { (_, meals) -> meals.maxOf { it.third } }
        return ApiResult.Success(History(counts, lastPlanned))
    }

    /**
     * The rules of the household for lunch and dinner, each with the recipes
     * its filter matches. A rule Mealie cannot evaluate is left out, as Mealie
     * would reject it too; without the permission to read rules, there are none.
     */
    private suspend fun rules(api: MealieApi): List<PlanRule> {
        val dtos = (apiCall { api.mealPlanRules() } as? ApiResult.Success)?.value?.items.orEmpty()
        return dtos.mapNotNull { dto ->
            val type = dto.entryType.takeIf { it != UNSET }?.let(MealType::fromApi)
            if (type != null && type != MealType.LUNCH && type != MealType.DINNER) return@mapNotNull null
            val filter = dto.queryFilterString.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val matching = (allRecipes(api, filter) as? ApiResult.Success)?.value ?: return@mapNotNull null
            PlanRule(
                day = dto.day.takeIf { it != UNSET }?.let { day -> DayOfWeek.entries.firstOrNull { it.name.equals(day, true) } },
                type = type,
                recipeIds = matching.map { it.id }.toSet(),
            )
        }
    }

    /** The full recipes, read a few at a time; one that cannot be read is skipped. */
    private suspend fun details(api: MealieApi, recipes: List<RecipeSummary>): ApiResult<List<Recipe>> = coroutineScope {
        val gate = Semaphore(PARALLEL_READS)
        val results = recipes.map { summary ->
            async { gate.withPermit { apiCall { api.recipe(summary.slug) } } }
        }.awaitAll()
        val read = results.mapNotNull { (it as? ApiResult.Success)?.value?.toDomain() }
        val failure = results.firstNotNullOfOrNull { it as? ApiResult.Failure }
        if (read.isEmpty() && failure != null) failure else ApiResult.Success(read)
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 30
        const val HISTORY_WEEKS = 12L
        const val SAMPLE_SIZE = 40
        const val SAMPLE_PER_RULE = 8
        const val PARALLEL_READS = 6
        const val MIN_DISH_INGREDIENTS = 3

        /** A recipe nothing places yet is less likely to be picked than a known dish. */
        const val UNPLACED_WEIGHT = 0.6
        const val UNSET = "unset"
    }
}
