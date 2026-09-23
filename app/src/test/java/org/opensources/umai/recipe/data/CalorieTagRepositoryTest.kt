package org.opensources.umai.recipe.data

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.query
import org.opensources.umai.recipe.domain.CalorieTag

class CalorieTagRepositoryTest {

    private lateinit var fake: FakeMealieServer
    private lateinit var repository: CalorieTagRepository

    @Before
    fun setUp() {
        fake = FakeMealieServer()
        repository = CalorieTagRepository { fake.api() }
    }

    @After
    fun tearDown() = fake.shutdown()

    @Test
    fun `a recipe with calories gets the tag, created when it does not exist`() = runTest {
        fake.enqueueJson(recipe(calories = "695 kcal", tags = """[{"id":"t1","name":"Rapide","slug":"rapide"}]"""))
        fake.enqueueJson(TAGS_EMPTY)
        fake.enqueueJson("""{"id":"t9","name":"calorie-695","slug":"calorie-695"}""", code = 201)
        fake.enqueueJson(recipe(calories = "695 kcal", tags = "[]"))

        val result = repository.sync("carbonara")

        assertTrue((result as ApiResult.Success).value)
        fake.takeRequest()
        assertEquals("calorie-695", fake.takeRequest().query("search"))
        val create = fake.takeRequest()
        assertEquals("/api/organizers/tags", create.url.encodedPath)
        assertTrue(create.body?.utf8().orEmpty().contains(""""name":"calorie-695""""))
        val put = fake.takeRequest().body?.utf8().orEmpty()
        assertTrue(put.contains(""""slug":"rapide""""))
        assertTrue(put.contains(""""slug":"calorie-695""""))
        // Everything else of the recipe goes back as it came.
        assertTrue(put.contains(""""extras":{"k":"v"}"""))
    }

    @Test
    fun `a recipe whose calories changed swaps its tag for the existing one`() = runTest {
        fake.enqueueJson(recipe(calories = "500", tags = """[{"id":"t2","name":"calorie-480","slug":"calorie-480"}]"""))
        fake.enqueueJson("""{"page":1,"per_page":200,"total":1,"total_pages":1,"items":[{"id":"t3","name":"calorie-500","slug":"calorie-500"}]}""")
        fake.enqueueJson(recipe(calories = "500", tags = "[]"))

        assertTrue((repository.sync("carbonara") as ApiResult.Success).value)

        repeat(2) { fake.takeRequest() }
        val put = fake.takeRequest().body?.utf8().orEmpty()
        assertTrue(put.contains(""""slug":"calorie-500""""))
        assertFalse(put.contains("calorie-480"))
        assertEquals(3, fake.server.requestCount)
    }

    @Test
    fun `a recipe already tagged right is not written`() = runTest {
        fake.enqueueJson(recipe(calories = "695 kcal", tags = """[{"id":"t9","name":"calorie-695","slug":"calorie-695"}]"""))

        assertFalse((repository.sync("carbonara") as ApiResult.Success).value)
        assertEquals(1, fake.server.requestCount)
    }

    @Test
    fun `a recipe without calories loses a stale calorie tag`() = runTest {
        fake.enqueueJson(recipe(calories = null, tags = """[{"id":"t9","name":"calorie-695","slug":"calorie-695"}]"""))
        fake.enqueueJson(recipe(calories = null, tags = "[]"))

        assertTrue((repository.sync("carbonara") as ApiResult.Success).value)

        fake.takeRequest()
        assertFalse(fake.takeRequest().body?.utf8().orEmpty().contains("calorie-695"))
    }

    @Test
    fun `the calorie tags of the instance are listed once`() = runTest {
        fake.enqueueJson(
            """{"page":1,"per_page":200,"total":3,"total_pages":1,"items":[
                {"id":"t1","name":"calorie-250","slug":"calorie-250"},
                {"id":"t2","name":"calories en trop","slug":"calories-en-trop"},
                {"id":"t3","name":"calorie-900","slug":"calorie-900"}]}""",
        )

        val first = (repository.calorieTags() as ApiResult.Success).value
        val second = (repository.calorieTags() as ApiResult.Success).value

        assertEquals(listOf(CalorieTag("t1", "calorie-250", 250), CalorieTag("t3", "calorie-900", 900)), first)
        assertEquals(first, second)
        assertEquals(1, fake.server.requestCount)
    }

    private companion object {
        const val TAGS_EMPTY = """{"page":1,"per_page":200,"total":0,"total_pages":0,"items":[]}"""

        fun recipe(calories: String?, tags: String): String {
            val nutrition = calories?.let { """{"calories":"$it"}""" } ?: "null"
            return """{"id":"r1","slug":"carbonara","name":"Carbonara","nutrition":$nutrition,"tags":$tags,"extras":{"k":"v"}}"""
        }
    }
}
