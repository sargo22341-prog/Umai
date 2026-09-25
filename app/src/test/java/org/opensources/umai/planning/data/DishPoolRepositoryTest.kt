package org.opensources.umai.planning.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.planning.domain.DishCourse
import org.opensources.umai.planning.domain.ModelCourseClassifier
import org.opensources.umai.youtube.domain.ScriptedModel
import java.time.LocalDate
import kotlin.random.Random

/** Keeps the courses in memory, as the device store would. */
class MemoryDishCourses(user: Map<String, DishCourse> = emptyMap()) : DishCourses {
    override val userCourses: Flow<Map<String, DishCourse>> = MutableStateFlow(user)
    val model = mutableMapOf<String, DishCourse>()
    override suspend fun setUserCourse(organizerId: String, course: DishCourse?) {
        val flow = userCourses as MutableStateFlow
        flow.value = if (course == null) flow.value - organizerId else flow.value + (organizerId to course)
    }
    override suspend fun modelCourses(): Map<String, DishCourse> = model
    override suspend fun rememberModelCourses(courses: Map<String, DishCourse>) {
        model += courses
    }
}

/**
 * A Mealie instance answering by path: the recipes of the collection, their
 * details, the past meals and the plan rules. The details are read in
 * parallel, so a queue of answers would not do.
 */
class PlanningMealie(private val fake: FakeMealieServer) {
    var rules = """{"items":[]}"""
    var history = """{"page":1,"total_pages":1,"items":[
        {"id":1,"date":"2026-09-10","entryType":"dinner","recipeId":"blanquette"}]}"""
    val created = mutableListOf<String>()

    fun install() {
        fake.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                val body = when {
                    path == "/api/recipes" && request.url.queryParameter("queryFilter") != null ->
                        page(RECIPES.filter { it.id == "gratin" })
                    path == "/api/recipes" -> page(RECIPES)
                    path.startsWith("/api/recipes/") -> RECIPES.first { path.endsWith("/${it.id}") }.detail()
                    path == "/api/households/mealplans/rules" -> rules
                    path == "/api/households/mealplans" && request.method == "POST" -> {
                        created += request.body?.utf8().orEmpty()
                        """{"id":${created.size},"date":"2026-09-25","entryType":"dinner"}"""
                    }
                    path == "/api/households/mealplans" -> history
                    else -> return MockResponse.Builder().code(404).build()
                }
                return MockResponse.Builder().code(200).setHeader("Content-Type", "application/json").body(body).build()
            }
        }
    }

    data class FakeRecipe(val id: String, val name: String, val category: String?, val ingredients: List<String>) {
        fun summary() = """{"id":"$id","slug":"$id","name":"$name","recipeCategory":[${
            category?.let { """{"id":"c-$it","name":"$it","slug":"$it"}""" }.orEmpty()
        }],"tags":[]}"""

        fun detail() = summary().dropLast(1) + ""","recipeIngredient":[${
            ingredients.joinToString(",") { """{"display":"$it","note":"$it"}""" }
        }],"recipeInstructions":[]}"""
    }

    private fun page(recipes: List<FakeRecipe>) =
        """{"page":1,"total_pages":1,"items":[${recipes.joinToString(",") { it.summary() }}]}"""

    companion object {
        val RECIPES = listOf(
            FakeRecipe("lasagnes", "Lasagnes", "Plats", listOf("250 g de pâtes", "500 g de bœuf haché", "1 oignon")),
            FakeRecipe("tiramisu", "Tiramisu", "Desserts", listOf("250 g de mascarpone", "100 g de sucre")),
            FakeRecipe("mojito", "Mojito", null, listOf("1 citron vert", "menthe", "rhum")),
            FakeRecipe("blanquette", "Blanquette", null, listOf("1 kg de veau", "1 oignon", "20 cl de crème")),
            FakeRecipe("moelleux", "Carré gourmand", null, listOf("200 g de chocolat", "100 g de sucre", "3 œufs", "farine")),
            FakeRecipe("gratin", "Gratin du jour", null, listOf("1 kg de pommes de terre", "20 cl de crème", "1 oignon")),
            FakeRecipe("tofu", "Bol mystère", null, listOf("tofu", "riz", "sauce soja", "gingembre")),
        )
    }
}

class DishPoolRepositoryTest {

    private lateinit var fake: FakeMealieServer
    private lateinit var mealie: PlanningMealie
    private val today = LocalDate.of(2026, 9, 25)

    @Before
    fun setUp() {
        fake = FakeMealieServer()
        mealie = PlanningMealie(fake).also { it.install() }
    }

    @After
    fun tearDown() = fake.shutdown()

    private fun repository(courses: DishCourses = MemoryDishCourses(), model: ScriptedModel = ScriptedModel(emptyList(), ready = false)) =
        DishPoolRepository({ fake.api() }, courses, ModelCourseClassifier(model))

    private suspend fun DishPoolRepository.ids(): Set<String> =
        (dishPool(today, Random(3)) { _, _ -> } as ApiResult.Success).value.candidates.map { it.recipe.id }.toSet()

    @Test
    fun `only dishes are kept, whatever tells them apart`() = runTest {
        // Lasagnes by their category, the blanquette by a past dinner, the gratin and the bowl by
        // their savoury ingredients; the tiramisu by its category, the mojito by its name and the
        // chocolate square by its sweet ingredients are left out.
        assertEquals(setOf("lasagnes", "blanquette", "gratin", "tofu"), repository().ids())
    }

    @Test
    fun `the user's choice for a category wins`() = runTest {
        val courses = MemoryDishCourses(mapOf("c-Plats" to DishCourse.OTHER))
        assertEquals(setOf("blanquette", "gratin", "tofu"), repository(courses).ids())
    }

    @Test
    fun `the model settles the recipes nothing places, and its answers are kept`() = runTest {
        val model = ScriptedModel(
            listOf(org.opensources.umai.llm.domain.LlmOutcome.Success("""{"items":[{"id":"r1","course":"main"},{"id":"r2","course":"dessert"},{"id":"r3","course":"other"}]}""")),
        )
        val courses = MemoryDishCourses()

        val ids = repository(courses, model).ids()

        // The three unplaced recipes are sent in the order they were read; which is which is
        // checked through what was remembered.
        assertEquals(3, courses.model.size)
        assertTrue(model.requests.single().user.contains("Carré gourmand"))
        val mains = courses.model.filterValues { it == DishCourse.MAIN }.keys
        assertEquals(setOf("lasagnes", "blanquette") + mains, ids)
    }

    @Test
    fun `a rule of the household comes with the recipes it allows`() = runTest {
        mealie.rules = """{"items":[{"id":"r","day":"friday","entryType":"dinner","queryFilterString":"tags.name = \"Four\""}]}"""

        val pool = (repository().dishPool(today, Random(3)) { _, _ -> } as ApiResult.Success).value

        val rule = pool.rules.single()
        assertEquals(java.time.DayOfWeek.FRIDAY, rule.day)
        assertEquals(setOf("gratin"), rule.recipeIds)
    }

    @Test
    fun `an unreachable instance is reported`() = runTest {
        fake.shutdown()
        val result = repository().dishPool(today, Random(3)) { _, _ -> }
        assertTrue((result as ApiResult.Failure).error is NetworkError.Unreachable)
    }

    @Test
    fun `the ingredients of planned dishes are read`() = runTest {
        val keys = repository().ingredientsOf(
            listOf(org.opensources.umai.planning.domain.summary("gratin")),
        )
        assertEquals(setOf("pomme terre", "creme", "oignon"), keys)
    }
}
