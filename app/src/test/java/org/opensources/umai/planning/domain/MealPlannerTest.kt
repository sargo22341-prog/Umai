package org.opensources.umai.planning.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.umai.core.model.MealType
import org.opensources.umai.llm.domain.LanguageModel
import org.opensources.umai.llm.domain.LlmOutcome
import org.opensources.umai.llm.domain.LlmProgress
import org.opensources.umai.llm.domain.LlmRequest
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

class MealPlannerTest {

    private val monday = LocalDate.of(2026, 9, 21)
    private val slots = listOf(
        MealSlot(monday, MealType.LUNCH),
        MealSlot(monday, MealType.DINNER),
        MealSlot(monday.plusDays(1), MealType.LUNCH),
    )

    /** Staples everywhere, then dishes sharing cream and leeks or each needing their own things. */
    private val candidates = listOf(
        candidate("gratin", "sel", "huile", "creme", "poireau", "pomme terre"),
        candidate("quiche", "sel", "huile", "creme", "poireau", "pate brisee", "oeuf"),
        candidate("veloute", "sel", "huile", "creme", "poireau", "bouillon"),
        candidate("sushi", "sel", "huile", "riz", "saumon", "algue", "vinaigre"),
        candidate("tacos", "sel", "huile", "tortilla", "boeuf", "avocat", "citron vert"),
        candidate("couscous", "sel", "huile", "semoule", "agneau", "pois chiche", "navet"),
        candidate("pho", "sel", "huile", "nouille", "gingembre", "anis", "boeuf"),
        candidate("paella", "sel", "huile", "riz", "moule", "chorizo", "safran"),
        candidate("chili", "sel", "huile", "haricot", "boeuf", "piment", "tomate"),
        candidate("risotto", "sel", "huile", "riz", "parmesan", "champignon", "vin blanc"),
    )

    @Test
    fun `every meal gets a dish, never the same twice`() {
        val proposal = MealPlanner(Random(1)).plan(slots, candidates)

        assertEquals(slots, proposal.meals.map { it.slot })
        assertEquals(3, proposal.meals.map { it.candidate.recipe.id }.distinct().size)
        assertTrue(proposal.unfilled.isEmpty())
    }

    @Test
    fun `the plan gathers dishes that share their ingredients`() {
        val proposal = MealPlanner(Random(1)).plan(slots, candidates)

        assertEquals(setOf("gratin", "quiche", "veloute"), proposal.meals.map { it.candidate.recipe.id }.toSet())
        // Salt and oil are in every dish: staples, left out of the count.
        assertEquals(6, proposal.ingredientCount)
        assertEquals(listOf("creme", "poireau"), proposal.shared.map { it.name })
        assertEquals(3, proposal.shared.first().recipeCount)
    }

    @Test
    fun `ingredients already needed that week pull the plan towards them`() {
        val proposal = MealPlanner(Random(1)).plan(
            slots = slots.take(1),
            candidates = candidates,
            alreadyNeeded = setOf("riz", "saumon", "algue", "vinaigre"),
        )
        assertEquals("sushi", proposal.meals.single().candidate.recipe.id)
    }

    @Test
    fun `a much better rated dish is preferred`() {
        val favourite = candidate("favourite", "truffe", "foie gras", quality = 1.0)
        val proposal = MealPlanner(Random(1)).plan(slots.take(1), listOf(candidate("plain", "pates", quality = 0.1), favourite))
        assertEquals("favourite", proposal.meals.single().candidate.recipe.id)
    }

    @Test
    fun `meals the rules allow no dish for stay empty`() {
        val rules = listOf(PlanRule(DayOfWeek.MONDAY, MealType.DINNER, recipeIds = emptySet()))
        val proposal = MealPlanner(Random(1)).plan(slots, candidates, allowed = { slot, candidate -> rules.allow(slot, candidate.recipe.id) })

        assertEquals(listOf(slots[1]), proposal.unfilled)
        assertEquals(2, proposal.meals.size)
    }

    @Test
    fun `a rule for fish on Monday dinner is followed`() {
        val rules = listOf(PlanRule(DayOfWeek.MONDAY, MealType.DINNER, recipeIds = setOf("sushi", "paella")))
        val proposal = MealPlanner(Random(1)).plan(slots, candidates, allowed = { slot, candidate -> rules.allow(slot, candidate.recipe.id) })

        assertTrue(proposal.meals[1].candidate.recipe.id in setOf("sushi", "paella"))
    }

    @Test
    fun `too few dishes leave meals empty`() {
        val proposal = MealPlanner(Random(1)).plan(slots, candidates.take(2))
        assertEquals(2, proposal.meals.size)
        assertEquals(1, proposal.unfilled.size)
    }

    @Test
    fun `swapping a meal keeps the others and never proposes a rejected dish`() {
        val planner = MealPlanner(Random(1))
        val proposal = planner.plan(slots, candidates)
        val first = proposal.meals[0].candidate.recipe.id

        val swapped = planner.replace(proposal, 0, candidates, rejected = setOf(first))

        assertNotEquals(first, swapped.meals[0].candidate.recipe.id)
        assertEquals(proposal.meals.drop(1), swapped.meals.drop(1))
        assertFalse(swapped.meals.map { it.candidate.recipe.id }.contains(first))
    }

    @Test
    fun `quality follows the rating and drops for a dish eaten lately`() {
        val today = LocalDate.of(2026, 9, 25)
        assertEquals(1.0, MealPlanner.quality(summary("a", rating = 5.0), today, null), 0.001)
        assertEquals(0.6, MealPlanner.quality(summary("a"), today, null), 0.001)
        assertEquals(0.2, MealPlanner.quality(summary("a", lastMade = "2026-09-20T19:00:00"), today, null), 0.001)
        assertEquals(0.2, MealPlanner.quality(summary("a"), today, lastPlanned = today.minusDays(3)), 0.001)
        assertEquals(0.6, MealPlanner.quality(summary("a"), today, lastPlanned = today.minusDays(40)), 0.001)
    }

    @Test
    fun `the sample favours the most welcome dishes but keeps the others possible`() {
        val pool = (1..100).map { it }
        val picked = (0 until 200).flatMap { seed -> MealPlanner.sample(pool, 10, Random(seed)) { if (it <= 10) 1.0 else 0.1 } }
        val favoured = picked.count { it <= 10 }
        assertTrue(favoured > picked.size / 3)
        assertTrue(picked.any { it > 10 })
    }
}

class ModelCourseClassifierTest {

    private class Answering(private val text: String, private val ready: Boolean = true) : LanguageModel {
        var asked: LlmRequest? = null
        override suspend fun isReady() = ready
        override val contextSize = 16_384
        override suspend fun generate(request: LlmRequest, onProgress: (LlmProgress) -> Unit): LlmOutcome {
            asked = request
            return LlmOutcome.Success(text)
        }
    }

    @Test
    fun `answers are matched back by the codes the model echoes`() = runBlocking {
        val model = Answering("""{"items":[{"id":"r2","course":"dessert"},{"id":"r1","course":"main"},{"id":"r9","course":"main"}]}""")

        val courses = ModelCourseClassifier(model).classify(
            listOf(UnplacedRecipe("uuid-a", "Blanquette", listOf("veau")), UnplacedRecipe("uuid-b", "Riz au lait", listOf("riz", "lait"))),
        )

        assertEquals(mapOf("uuid-a" to DishCourse.MAIN, "uuid-b" to DishCourse.DESSERT), courses)
        assertTrue(requireNotNull(model.asked).user.contains("r1: Blanquette — veau"))
    }

    @Test
    fun `without a model nothing is asked`() = runBlocking {
        val model = Answering("{}", ready = false)
        assertTrue(ModelCourseClassifier(model).classify(listOf(UnplacedRecipe("a", "b", emptyList()))).isEmpty())
        assertEquals(null, model.asked)
    }
}
