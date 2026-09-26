package org.opensources.umai.planning.data

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.planning.domain.organizer
import org.opensources.umai.planning.domain.summary

class RecipeCaloriesRepositoryTest {

    private lateinit var fake: FakeMealieServer
    private lateinit var repository: RecipeCaloriesRepository
    private var instance = "https://mealie.lan/|u1"

    @Before
    fun setUp() {
        fake = FakeMealieServer()
        repository = RecipeCaloriesRepository(apiProvider = { fake.api() }, instanceKey = { instance })
    }

    @After
    fun tearDown() = fake.shutdown()

    private fun recipe(calories: String?) =
        """{"id":"r2","slug":"r2","name":"Tarte","nutrition":{"calories":${calories?.let { "\"$it\"" } ?: "null"}}}"""

    @Test
    fun `a recipe with its calorie tag costs no request`() = runTest {
        val tagged = summary("r1", tags = listOf(organizer("calorie-450", "calorie-450")))

        assertEquals(mapOf("r1" to 450), repository.calories(listOf(tagged, tagged)))
        assertEquals(0, fake.server.requestCount)
    }

    @Test
    fun `a recipe without the tag has its nutrition asked once`() = runTest {
        fake.enqueueJson(recipe("380 kcal"))

        assertEquals(mapOf("r2" to 380), repository.calories(listOf(summary("r2"))))
        assertEquals(mapOf("r2" to 380), repository.calories(listOf(summary("r2"))))

        assertEquals("/api/recipes/r2", fake.takeRequest().url.encodedPath)
        assertEquals(1, fake.server.requestCount)
    }

    @Test
    fun `a recipe without calories is known to have none`() = runTest {
        fake.enqueueJson(recipe(null))

        assertEquals(mapOf("r2" to null), repository.calories(listOf(summary("r2"))))
        repository.calories(listOf(summary("r2")))

        assertEquals(1, fake.server.requestCount)
    }

    @Test
    fun `a failure is not remembered, and forgetting asks again`() = runTest {
        fake.enqueueError(500)
        fake.enqueueJson(recipe("380 kcal"))
        fake.enqueueJson(recipe("410 kcal"))

        assertEquals(mapOf("r2" to null), repository.calories(listOf(summary("r2"))))
        assertEquals(mapOf("r2" to 380), repository.calories(listOf(summary("r2"))))
        repository.forget()
        assertEquals(mapOf("r2" to 410), repository.calories(listOf(summary("r2"))))
    }

    @Test
    fun `what was read from one instance is asked again on another`() = runTest {
        fake.enqueueJson(recipe("380 kcal"))
        fake.enqueueJson(recipe("410 kcal"))

        assertEquals(mapOf("r2" to 380), repository.calories(listOf(summary("r2"))))
        instance = "https://other.lan/|u1"
        assertEquals(mapOf("r2" to 410), repository.calories(listOf(summary("r2"))))
        assertEquals(2, fake.server.requestCount)
    }
}
