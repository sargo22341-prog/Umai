package org.opensources.umai.planning.domain

import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.model.RecipeSummary
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ln
import kotlin.random.Random

/** One meal to fill: lunch or dinner on a day. */
data class MealSlot(val date: LocalDate, val type: MealType)

/**
 * A dish that can be planned: [ingredients] are what it needs to buy
 * ([IngredientKeys]), [labels] how they read, [quality] how welcome it is,
 * from 0 to 1.
 */
data class PlanCandidate(
    val recipe: RecipeSummary,
    val ingredients: Set<String>,
    val quality: Double,
    val labels: Map<String, String> = emptyMap(),
)

data class PlannedMeal(val slot: MealSlot, val candidate: PlanCandidate)

/** An ingredient several of the planned dishes need, as it reads. */
data class SharedIngredient(val name: String, val recipeCount: Int)

/** A plan proposed for the empty meals, before it is written on Mealie. */
data class MealPlanProposal(
    val meals: List<PlannedMeal>,
    /** Meals no dish could fill: too few dishes, or none the rules of the household allow. */
    val unfilled: List<MealSlot>,
    /** The different ingredients to buy for the whole plan, staples such as salt aside. */
    val ingredientCount: Int,
    val shared: List<SharedIngredient>,
)

/**
 * Plans dishes on empty meals so that the week needs few different
 * ingredients, the way meal-kit planners do: a pot of cream opened on Monday
 * finishes in a gratin on Wednesday rather than in the bin.
 *
 * A plan is scored on three things:
 * - how welcome each dish is (rating, not cooked or planned lately);
 * - how many different ingredients the whole plan needs, each weighted by how
 *   rare it is among the dishes: salt, oil or onions, which nearly every dish
 *   needs, count for little, and the ingredients already needed by the meals
 *   planned that week count for nothing;
 * - whether two meals in a row are nearly the same dish, so sharing
 *   ingredients does not mean eating the same thing twice a day.
 *
 * The plan is built greedily, one meal at a time, then improved by trying
 * every other dish in every meal until nothing improves. [random] adds a
 * little noise to the welcome of each dish, and the dishes of a plan the user
 * asked to redo are avoided, so asking again gives another good plan.
 */
class MealPlanner(private val random: Random) {

    fun plan(
        slots: List<MealSlot>,
        candidates: List<PlanCandidate>,
        alreadyNeeded: Set<String> = emptySet(),
        avoid: Set<String> = emptySet(),
        allowed: (MealSlot, PlanCandidate) -> Boolean = { _, _ -> true },
    ): MealPlanProposal {
        val context = Context(candidates, alreadyNeeded, avoid, random)
        val assignment = arrayOfNulls<PlanCandidate>(slots.size)

        slots.indices.forEach { index ->
            assignment[index] = context.best(slots, assignment, index, allowed, exclude = emptySet())
        }
        var passes = 0
        var improved = true
        while (improved && passes < MAX_PASSES) {
            improved = false
            passes++
            slots.indices.forEach { index ->
                val current = assignment[index] ?: return@forEach
                val before = context.score(assignment)
                assignment[index] = null
                val best = context.best(slots, assignment, index, allowed, exclude = emptySet()) ?: current
                assignment[index] = best
                if (context.score(assignment) > before + EPSILON) improved = true else assignment[index] = current
            }
        }
        return context.proposal(slots, assignment)
    }

    /**
     * Another dish for meal [index] of [proposal], the others staying: the
     * best one not planned yet and not in [rejected].
     */
    fun replace(
        proposal: MealPlanProposal,
        index: Int,
        candidates: List<PlanCandidate>,
        rejected: Set<String>,
        alreadyNeeded: Set<String> = emptySet(),
        allowed: (MealSlot, PlanCandidate) -> Boolean = { _, _ -> true },
    ): MealPlanProposal {
        val slots = proposal.meals.map { it.slot }
        val assignment = proposal.meals.map { it.candidate }.toTypedArray<PlanCandidate?>()
        val context = Context(candidates, alreadyNeeded, emptySet(), random)
        val current = assignment[index]
        assignment[index] = null
        val exclude = rejected + listOfNotNull(current?.recipe?.id)
        assignment[index] = context.best(slots, assignment, index, allowed, exclude) ?: current
        return context.proposal(slots, assignment, proposal.unfilled)
    }

    private class Context(
        candidates: List<PlanCandidate>,
        private val alreadyNeeded: Set<String>,
        private val avoid: Set<String>,
        random: Random,
    ) {

        /** How much an ingredient costs to add: the rarer among the dishes, the dearer. */
        private val weights: Map<String, Double> = run {
            val counts = candidates.flatMap { it.ingredients }.groupingBy { it }.eachCount()
            val total = candidates.size.coerceAtLeast(1)
            counts.mapValues { (_, count) ->
                val share = count.toDouble() / total
                if (total >= MIN_POOL_FOR_STAPLES && share >= STAPLE_SHARE) STAPLE_WEIGHT else 1.0
            }
        }

        private val noise: Map<String, Double> = candidates.associate { it.recipe.id to (random.nextDouble() * 2 - 1) * NOISE }

        private val pool = candidates.distinctBy { it.recipe.id }

        fun weight(ingredient: String): Double = weights[ingredient] ?: 1.0

        fun score(assignment: Array<PlanCandidate?>): Double {
            val planned = assignment.filterNotNull()
            val welcome = planned.sumOf {
                QUALITY_WEIGHT * (it.quality + (noise[it.recipe.id] ?: 0.0)) - if (it.recipe.id in avoid) AVOID_COST else 0.0
            }
            val needed = planned.flatMap { it.ingredients }.toSet() - alreadyNeeded
            val shopping = needed.sumOf { weight(it) } * INGREDIENT_COST
            val sameness = assignment.toList().zipWithNext().sumOf { (a, b) ->
                if (a == null || b == null) 0.0 else (similarity(a, b) - SIMILAR_DISH).coerceAtLeast(0.0) * SIMILARITY_COST
            }
            // Filling a meal always beats leaving it empty.
            return planned.size * FILL_BONUS + welcome - shopping - sameness
        }

        fun best(
            slots: List<MealSlot>,
            assignment: Array<PlanCandidate?>,
            index: Int,
            allowed: (MealSlot, PlanCandidate) -> Boolean,
            exclude: Set<String>,
        ): PlanCandidate? {
            val used = assignment.filterNotNull().map { it.recipe.id }.toSet()
            return pool
                .filter { it.recipe.id !in used && it.recipe.id !in exclude && allowed(slots[index], it) }
                .maxByOrNull { candidate ->
                    assignment[index] = candidate
                    score(assignment).also { assignment[index] = null }
                }
        }

        private fun similarity(a: PlanCandidate, b: PlanCandidate): Double {
            val union = (a.ingredients + b.ingredients).sumOf { weight(it) }
            if (union == 0.0) return 0.0
            return (a.ingredients intersect b.ingredients).sumOf { weight(it) } / union
        }

        fun proposal(
            slots: List<MealSlot>,
            assignment: Array<PlanCandidate?>,
            unfilledBefore: List<MealSlot> = emptyList(),
        ): MealPlanProposal {
            val meals = slots.indices.mapNotNull { i -> assignment[i]?.let { PlannedMeal(slots[i], it) } }
            val unfilled = unfilledBefore + slots.indices.filter { assignment[it] == null }.map { slots[it] }
            val counts = meals.flatMap { it.candidate.ingredients }.groupingBy { it }.eachCount()
            val kept = counts.keys.filter { weight(it) == 1.0 }
            val labels = meals.flatMap { it.candidate.labels.entries }.associate { it.key to it.value }
            return MealPlanProposal(
                meals = meals,
                unfilled = unfilled,
                ingredientCount = kept.size,
                shared = kept.filter { counts.getValue(it) > 1 }
                    .map { SharedIngredient(labels[it] ?: it, counts.getValue(it)) }
                    .sortedWith(compareByDescending<SharedIngredient> { it.recipeCount }.thenBy { it.name }),
            )
        }
    }

    companion object {
        private const val QUALITY_WEIGHT = 3.0
        private const val INGREDIENT_COST = 0.35
        private const val SIMILARITY_COST = 5.0

        /** Two dishes sharing more than this share of what they need are nearly the same dish. */
        private const val SIMILAR_DISH = 0.6
        private const val FILL_BONUS = 10.0
        private const val NOISE = 0.1
        private const val AVOID_COST = 1.5
        private const val MAX_PASSES = 4
        private const val EPSILON = 1e-9

        /** An ingredient in this share of the dishes or more is a staple, bought anyway. */
        private const val STAPLE_SHARE = 0.35
        private const val STAPLE_WEIGHT = 0.1
        private const val MIN_POOL_FOR_STAPLES = 10

        /** Days after which a dish cooked or planned is welcome again. */
        private const val RECENT_DAYS = 21L

        /**
         * How welcome a dish is, from 0 to 1: its rating (an unrated dish is
         * average), less when it was cooked or planned in the last weeks.
         */
        fun quality(recipe: RecipeSummary, today: LocalDate, lastPlanned: LocalDate?): Double {
            val rating = (recipe.rating?.takeIf { it > 0 } ?: UNRATED) / MAX_RATING
            val lastMade = recipe.lastMade?.take(DATE_LENGTH)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val recent = listOfNotNull(lastMade, lastPlanned).maxOrNull()
                ?.let { ChronoUnit.DAYS.between(it, today) in 0..RECENT_DAYS } == true
            return (rating - if (recent) RECENT_PENALTY else 0.0).coerceIn(0.0, 1.0)
        }

        /**
         * Picks [count] dishes out of [pool], at random but favouring the most
         * welcome ones (Efraimidis–Spirakis weighted sampling): the details of
         * each dish are then read, which is too slow for a whole collection.
         */
        fun <T> sample(pool: List<T>, count: Int, random: Random, weight: (T) -> Double): List<T> =
            pool.map { item ->
                val w = weight(item).coerceAtLeast(MIN_SAMPLE_WEIGHT)
                item to ln(random.nextDouble().coerceAtLeast(Double.MIN_VALUE)) / w
            }.sortedByDescending { it.second }.take(count).map { it.first }

        private const val UNRATED = 3.0
        private const val MAX_RATING = 5.0
        private const val RECENT_PENALTY = 0.4
        private const val DATE_LENGTH = 10
        private const val MIN_SAMPLE_WEIGHT = 0.05
    }
}
