package org.opensources.umai.planning.domain

import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.model.Organizer
import org.opensources.umai.core.model.RecipeSummary
import kotlin.math.min

/**
 * Decides what course a recipe is from what the instance says about it,
 * nothing being written in advance:
 *
 * 1. the course the user gave one of its categories or tags, which settles it;
 * 2. the course its categories and tags name ([CourseVocabulary]);
 * 3. the dish its name gives away ("Tiramisu", "Mojito");
 * 4. how it was planned before in Mealie: a recipe put at dinner is a dish,
 *    one put as a dessert is a dessert.
 *
 * Each clue is a vote. A recipe with any clue of being something else than a
 * dish is kept out unless the dish clues outweigh it; a recipe without any
 * clue is left undecided (`null`), for its ingredients or the language model
 * to settle.
 */
object CourseClassifier {

    fun classify(
        recipe: RecipeSummary,
        organizerCourse: (Organizer) -> DishCourse?,
        userCourses: Map<String, DishCourse>,
        pastMeals: Map<MealType, Int>,
    ): DishCourse? {
        val organizers = recipe.categories + recipe.tags
        val chosen = organizers.mapNotNull { userCourses[it.id] }
        if (chosen.isNotEmpty()) return chosen.firstOrNull { it != DishCourse.MAIN } ?: DishCourse.MAIN

        val votes = mutableMapOf<DishCourse, Int>()
        fun vote(course: DishCourse, weight: Int) = votes.merge(course, weight, Int::plus)
        organizers.forEach { organizer -> organizerCourse(organizer)?.let { vote(it, ORGANIZER_WEIGHT) } }
        CourseVocabulary.ofRecipeName(recipe.name)?.let { vote(it, NAME_WEIGHT) }
        pastMeals.forEach { (type, count) -> courseOf(type)?.let { vote(it, min(count, MAX_HISTORY_WEIGHT)) } }

        if (votes.isEmpty()) return null
        val dish = votes[DishCourse.MAIN] ?: 0
        val other = votes.filterKeys { it != DishCourse.MAIN }.maxByOrNull { it.value }
        return if (other != null && other.value >= dish) other.key else DishCourse.MAIN
    }

    /** The course a slot of Mealie's meal plan holds. */
    fun courseOf(type: MealType): DishCourse? = when (type) {
        MealType.LUNCH, MealType.DINNER -> DishCourse.MAIN
        MealType.DESSERT -> DishCourse.DESSERT
        MealType.DRINK -> DishCourse.DRINK
        MealType.SIDE, MealType.SNACK, MealType.BREAKFAST -> DishCourse.OTHER
    }

    private const val ORGANIZER_WEIGHT = 2
    private const val NAME_WEIGHT = 3
    private const val MAX_HISTORY_WEIGHT = 3
}
