package org.opensources.umai.recipe.data

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.recipe.domain.DraftOrganizer
import org.opensources.umai.recipe.domain.DraftStep
import org.opensources.umai.recipe.domain.RecipeDraft

class RecipeEditRepositoryTest {

    private lateinit var fake: FakeMealieServer
    private lateinit var repository: RecipeEditRepository

    @Before
    fun setUp() {
        fake = FakeMealieServer()
        repository = RecipeEditRepository { fake.api() }
    }

    @After
    fun tearDown() = fake.shutdown()

    @Test
    fun `importing a page hands the address to Mealie's own scraper`() = runTest {
        fake.enqueueJson(""""tarte-aux-pommes"""")

        val slug = repository.importFromUrl(
            url = " https://example.org/tarte ",
            includeTags = true,
            includeCategories = false,
        )

        assertEquals("tarte-aux-pommes", (slug as ApiResult.Success).value)
        val request = fake.takeRequest()
        assertEquals("/api/recipes/create/url", request.url.encodedPath)
        val body = request.body?.utf8().orEmpty()
        assertTrue(body.contains(""""url":"https://example.org/tarte""""))
        assertTrue(body.contains(""""includeTags":true"""))
        assertTrue(body.contains(""""includeCategories":false"""))
    }

    @Test
    fun `an empty address never reaches the server`() = runTest {
        val result = repository.importFromUrl("  ", includeTags = false, includeCategories = false)

        assertEquals(NetworkError.InvalidResponse, (result as ApiResult.Failure).error)
        assertEquals(0, fake.server.requestCount)
    }

    @Test
    fun `a draft without a name is refused before any request`() = runTest {
        val result = repository.create(RecipeDraft(id = "d1", name = "   "))

        assertEquals(NetworkError.InvalidResponse, (result as ApiResult.Failure).error)
        assertEquals(0, fake.server.requestCount)
    }

    @Test
    fun `creating a draft posts the name then sends the details`() = runTest {
        fake.enqueueJson(""""gratin-de-courgettes"""")
        fake.enqueueJson(CREATED_RECIPE)
        fake.enqueueJson(CREATED_RECIPE)

        val result = repository.create(
            RecipeDraft(
                id = "d1",
                name = " Gratin de courgettes ",
                description = "Simple",
                servings = 4,
                prepTime = "15 minutes",
                cookTime = "40 minutes",
                ingredients = listOf("2 courgettes", "  ", "Creme"),
                steps = listOf(DraftStep(title = "Four", text = "Prechauffer."), DraftStep()),
                categories = listOf(DraftOrganizer("c1", "Plat", "plat")),
                tags = listOf(DraftOrganizer("t1", "Ete", "ete")),
            ),
        )

        assertEquals("gratin-de-courgettes", (result as ApiResult.Success).value)

        val create = fake.takeRequest()
        assertEquals("/api/recipes", create.url.encodedPath)
        assertTrue(create.body?.utf8().orEmpty().contains(""""name":"Gratin de courgettes""""))

        // The recipe is read back before being updated, so the payload keeps
        // whatever Mealie generated for it.
        assertEquals("/api/recipes/gratin-de-courgettes", fake.takeRequest().url.encodedPath)

        val update = fake.takeRequest()
        assertEquals("PUT", update.method)
        assertEquals("/api/recipes/gratin-de-courgettes", update.url.encodedPath)
        val body = update.body?.utf8().orEmpty()
        assertTrue(body.contains(""""recipeServings":4"""))
        assertTrue(body.contains(""""prepTime":"15 minutes""""))
        // Mealie's own editor labels `performTime` "cook time".
        assertTrue(body.contains(""""performTime":"40 minutes""""))
        assertTrue(body.contains(""""note":"2 courgettes""""))
        assertTrue(body.contains(""""note":"Creme""""))
        assertTrue(body.contains(""""text":"Prechauffer.""""))
        assertTrue(body.contains(""""slug":"plat""""))
        assertTrue(body.contains(""""slug":"ete""""))
    }

    @Test
    fun `blank ingredient lines and empty steps are dropped`() = runTest {
        fake.enqueueJson(""""test"""")
        fake.enqueueJson(CREATED_RECIPE)
        fake.enqueueJson(CREATED_RECIPE)

        repository.create(
            RecipeDraft(
                id = "d1",
                name = "Test",
                ingredients = listOf("Sel", "   ", ""),
                steps = listOf(DraftStep(), DraftStep(text = "Melanger.")),
            ),
        )

        fake.takeRequest()
        fake.takeRequest()
        val body = fake.takeRequest().body?.utf8().orEmpty()

        assertEquals(1, Regex(""""note":""").findAll(body).count())
        assertEquals(1, Regex(""""text":"Melanger\.""").findAll(body).count())
    }

    @Test
    fun `a refused creation stops before the update`() = runTest {
        fake.enqueueError(403)

        val result = repository.create(RecipeDraft(id = "d1", name = "Test"))

        assertEquals(NetworkError.Unauthorized, (result as ApiResult.Failure).error)
        assertEquals(1, fake.server.requestCount)
    }

    private companion object {
        val CREATED_RECIPE = """
            {"id":"r1","name":"Gratin de courgettes","slug":"gratin-de-courgettes",
             "recipeIngredient":[],"recipeInstructions":[]}
        """.trimIndent()
    }
}
