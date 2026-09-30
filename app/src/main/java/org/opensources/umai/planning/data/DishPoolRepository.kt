package org.opensources.umai.planning.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import org.opensources.umai.core.network.fetchAllPages
import org.opensources.umai.core.network.isRetryable
import org.opensources.umai.core.network.toPaged
import org.opensources.umai.core.network.valueOr
import org.opensources.umai.core.network.valueOrNull
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
 *    model when there is one, loaded while the dishes are read, and otherwise
 *    judged by their ingredients.
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
        val recipes = allRecipes(api, queryFilter = null).valueOr { return it }
        val history = history(api, today).valueOr { return it }
        val rules = rules(api).valueOr { return it }
        val placed = place(recipes, history)
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
        val details = coroutineScope {
            // The model loads while the dishes are read, when some of them will need it.
            if (sampled.any { placed[it.id] == null }) launch { modelClassifier.prepare() }
            details(api, sampled)
        }.valueOr { return it }

        val decided = recognize(details.filter { placed[it.id] == null }, onPhase)
        val candidates = details.mapNotNull { recipe ->
            val keys = IngredientKeys.keysOf(recipe.ingredients)
            val course = placed[recipe.id] ?: decided[recipe.id] ?: byIngredients(keys)
            if (course != DishCourse.MAIN) return@mapNotNull null
            PlanCandidate(recipe.summary, keys, quality.getValue(recipe.id), IngredientKeys.labelsOf(recipe.ingredients))
        }
        return ApiResult.Success(DishPool(candidates, rules))
    }

    /** The course of each recipe, from what the user chose, its organizers, its name, its past meals or the model's answer. */
    private suspend fun place(recipes: List<RecipeSummary>, history: History): Map<String, DishCourse?> {
        val userCourses = courses.userCourses.first()
        val modelCourses = courses.modelCourses()
        return recipes.associate { recipe ->
            val course = CourseClassifier.classify(
                recipe = recipe,
                organizerCourse = { CourseVocabulary.ofOrganizer(it.name) },
                userCourses = userCourses,
                pastMeals = history.counts[recipe.id].orEmpty(),
            ) ?: modelCourses[recipe.id]
            recipe.id to course
        }
    }

    /** The courses the model gives the dishes nothing else placed, remembered for the next plans. */
    private suspend fun recognize(unplaced: List<Recipe>, onPhase: (DishPoolPhase) -> Unit): Map<String, DishCourse> {
        if (unplaced.isEmpty()) return emptyMap()
        onPhase(DishPoolPhase.RECOGNIZING)
        return modelClassifier.classify(
            unplaced.map { recipe ->
                UnplacedRecipe(recipe.id, recipe.name, recipe.ingredients.mapNotNull(IngredientKeys::keyOf).distinct())
            },
        ).also { courses.rememberModelCourses(it) }
    }

    /**
     * What the dishes already planned need: the new ones are chosen to use it
     * up. A dish that cannot be read counts for nothing rather than failing.
     */
    suspend fun ingredientsOf(recipes: List<RecipeSummary>): Set<String> {
        val api = apiProvider() ?: return emptySet()
        val read = details(api, recipes.distinctBy { it.id }).valueOrNull().orEmpty()
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

    /**
     * The recipes [queryFilter] matches, all of them when it is `null`. Past
     * [MAX_PAGES], the plan is made from the first recipes by name: a plan from
     * thousands of dishes is still a plan.
     */
    private suspend fun allRecipes(api: MealieApi, queryFilter: String?): ApiResult<List<RecipeSummary>> = apiCall {
        fetchAllPages(MAX_PAGES) { page ->
            api.recipes(page = page, perPage = PAGE_SIZE, orderBy = "name", orderDirection = "asc", queryFilter = queryFilter)
                .toPaged { it.toDomain() }
        }.items
    }

    private class History(val counts: Map<String, Map<MealType, Int>>, val lastPlanned: Map<String, LocalDate>)

    /**
     * How each recipe was planned over the last weeks, and when it last was as
     * a meal. [HISTORY_WEEKS] of meals are far from [MAX_PAGES] pages of them.
     */
    private suspend fun history(api: MealieApi, today: LocalDate): ApiResult<History> = apiCall {
        val entries = fetchAllPages(MAX_PAGES) { page ->
            api.mealPlans(
                startDate = ApiDates.format(today.minusWeeks(HISTORY_WEEKS)),
                endDate = ApiDates.format(today.minusDays(1)),
                page = page,
            ).toPaged { entry ->
                val id = entry.recipeId ?: entry.recipe?.id ?: return@toPaged null
                val date = ApiDates.parseDate(entry.date) ?: return@toPaged null
                Triple(id, MealType.fromApi(entry.entryType), date)
            }
        }.items
        val counts = entries.groupBy { it.first }.mapValues { (_, meals) -> meals.groupingBy { it.second }.eachCount() }
        val lastPlanned = entries.filter { CourseClassifier.courseOf(it.second) == DishCourse.MAIN }
            .groupBy { it.first }
            .mapValues { (_, meals) -> meals.maxOf { it.third } }
        History(counts, lastPlanned)
    }

    /**
     * The rules of the household for lunch and dinner, each with the recipes
     * its filter matches, read a few at a time. Without the permission to read
     * rules there are none; a rule whose filter Mealie rejects is left out, as
     * Mealie leaves it out too. Any other failure is the plan's.
     */
    private suspend fun rules(api: MealieApi): ApiResult<List<PlanRule>> {
        val dtos = when (val read = apiCall { api.mealPlanRules() }) {
            is ApiResult.Success -> read.value.items
            is ApiResult.Failure -> return if (read.error == NetworkError.Forbidden) ApiResult.Success(emptyList()) else read
        }
        val wanted = dtos.mapNotNull { dto ->
            val type = dto.entryType.takeIf { it != UNSET }?.let(MealType::fromApi)
            if (type != null && type != MealType.LUNCH && type != MealType.DINNER) return@mapNotNull null
            val filter = dto.queryFilterString.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val day = dto.day.takeIf { it != UNSET }?.let { day -> DayOfWeek.entries.firstOrNull { it.name.equals(day, true) } }
            PlanRule(day = day, type = type, recipeIds = emptySet()) to filter
        }
        val matched = coroutineScope {
            val gate = Semaphore(PARALLEL_READS)
            wanted.map { (rule, filter) -> async { gate.withPermit { rule to allRecipes(api, filter) } } }.awaitAll()
        }
        return ApiResult.Success(
            matched.mapNotNull { (rule, matching) ->
                when (matching) {
                    is ApiResult.Success -> rule.copy(recipeIds = matching.value.map { it.id }.toSet())
                    // Refused as such, not for now (408, 429): the filter itself is at fault.
                    is ApiResult.Failure -> if (matching.error is NetworkError.Http && !matching.error.isRetryable) null else return matching
                }
            },
        )
    }

    /** The full recipes, read a few at a time; one that cannot be read is skipped. */
    private suspend fun details(api: MealieApi, recipes: List<RecipeSummary>): ApiResult<List<Recipe>> = coroutineScope {
        val gate = Semaphore(PARALLEL_READS)
        val results = recipes.map { summary ->
            async { gate.withPermit { apiCall { api.recipe(summary.slug) } } }
        }.awaitAll()
        val read = results.mapNotNull { it.valueOrNull()?.toDomain() }
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
