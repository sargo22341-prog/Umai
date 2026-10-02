package org.opensources.umai.planning.domain

import org.opensources.umai.core.model.MealPlanEntry
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.recipe.domain.CalorieTags

/**
 * The calories of one day of the plan: [total] adds up those of every entry
 * that has some, [unknown] counts the entries without, which the total leaves out.
 */
data class DayCalories(val total: Int, val unknown: Int) {
    val isEmpty: Boolean get() = total == 0 && unknown == 0
}

/**
 * Counts the calories of the plan: Mealie's nutrition is written for one
 * serving of a recipe, and a food added to the plan carries the calories of
 * one portion in its note (see [FoodNote]). Both count as many times as the
 * entry has servings.
 */
object PlanCalories {

    // A thousands separator may be a space, a no-break space or a narrow one.
    private val kcal = Regex("""(\d{1,3}(?:[   ]\d{3})+|\d+(?:[.,]\d+)?)\s*kcal""", RegexOption.IGNORE_CASE)

    /** The calories written in a note, such as "139 kcal · 330 ml"; `null` when it gives none. */
    fun ofNote(text: String): Int? {
        val match = kcal.find(text) ?: return null
        val value = match.groupValues[1]
            .filterNot { it == ' ' || it == ' ' || it == ' ' }
            .replace(',', '.')
            .toDoubleOrNull()
            ?: return null
        return Math.round(value).toInt()
    }

    /** The calories a recipe's tag holds, kept in step with its nutrition (see [CalorieTags]). */
    fun ofTags(recipe: RecipeSummary): Int? = recipe.tags.firstNotNullOfOrNull { CalorieTags.valueOf(it.slug) }

    /**
     * The calories of [entry], all its servings counted: a recipe's are looked
     * up in [recipeCalories], by recipe id; a note's are read in its text, or
     * else in its title.
     */
    fun ofEntry(entry: MealPlanEntry, recipeCalories: Map<String, Int?>): Int? {
        val recipe = entry.recipe
        val serving = if (recipe != null) recipeCalories[recipe.id] else ofNote(entry.text) ?: ofNote(entry.title)
        return serving?.let { it * entry.servings }
    }

    fun ofDay(entries: List<MealPlanEntry>, recipeCalories: Map<String, Int?>): DayCalories {
        val calories = entries.map { ofEntry(it, recipeCalories) }
        return DayCalories(total = calories.sumOf { it ?: 0 }, unknown = calories.count { it == null })
    }
}
