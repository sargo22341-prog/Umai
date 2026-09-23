package org.opensources.umai.recipe.data

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.model.Recipe
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.query
import org.opensources.umai.core.network.queryValues
import org.opensources.umai.recipe.domain.CalorieFilter
import org.opensources.umai.recipe.domain.CalorieTag
import org.opensources.umai.search.domain.AddedWithin
import org.opensources.umai.search.domain.RecipeFilters
import org.opensources.umai.search.domain.RecipeSort
import org.opensources.umai.search.domain.SortField
import java.time.Instant

class RecipeRepositoryTest {

    private lateinit var fake: FakeMealieServer
    private lateinit var repository: RecipeRepository

    @Before
    fun setUp() {
        fake = FakeMealieServer()
        repository = RecipeRepository(
            apiProvider = { fake.api() },
            currentUserId = { "user-1" },
        )
    }

    @After
    fun tearDown() = fake.shutdown()

    @Test
    fun `search parses a page of recipes`() = runTest {
        fake.enqueueJson(PAGE)

        val result = repository.search("curry", RecipeFilters.None, page = 1)

        assertTrue(result is ApiResult.Success)
        val page = (result as ApiResult.Success).value
        assertEquals(2, page.items.size)
        assertEquals("Poulet au curry", page.items[0].name)
        assertEquals(1, page.page)
        assertEquals(3, page.totalPages)
        assertEquals(58, page.total)
        assertTrue(page.hasNext)
    }

    @Test
    fun `search hits the documented endpoint with the search term`() = runTest {
        fake.enqueueJson(PAGE)
        repository.search("curry", RecipeFilters.None, page = 2, perPage = 10)

        val request = fake.takeRequest()
        assertEquals("/api/recipes", request.url.encodedPath)
        assertEquals("curry", request.query("search"))
        assertEquals("2", request.query("page"))
        assertEquals("10", request.query("perPage"))
        assertEquals("createdAt", request.query("orderBy"))
        assertEquals("desc", request.query("orderDirection"))
    }

    @Test
    fun `organizer filters are sent as repeated query parameters`() = runTest {
        fake.enqueueJson(PAGE)
        repository.search(
            query = null,
            filters = RecipeFilters(
                categoryIds = setOf("c1", "c2"),
                requireAllCategories = true,
                tagIds = setOf("t1"),
                toolIds = setOf("tool1"),
                foodIds = setOf("f1"),
            ),
            page = 1,
        )

        val request = fake.takeRequest()
        assertEquals(setOf("c1", "c2"), request.queryValues("categories").toSet())
        assertEquals(listOf("t1"), request.queryValues("tags"))
        assertEquals(listOf("tool1"), request.queryValues("tools"))
        assertEquals(listOf("f1"), request.queryValues("foods"))
        assertEquals("true", request.query("requireAllCategories"))
    }

    @Test
    fun `requireAll is omitted when a single value is selected`() = runTest {
        fake.enqueueJson(PAGE)
        repository.search(
            query = null,
            filters = RecipeFilters(tagIds = setOf("t1"), requireAllTags = true),
            page = 1,
        )
        assertEquals(null, fake.takeRequest().query("requireAllTags"))
    }

    @Test
    fun `value filters are translated into a queryFilter expression`() = runTest {
        fake.enqueueJson(PAGE)
        repository.search(
            query = null,
            filters = RecipeFilters(minRating = 4, addedWithin = AddedWithin.ANY),
            page = 1,
        )
        assertEquals("rating >= 4", fake.takeRequest().query("queryFilter"))
    }

    @Test
    fun `favourites are resolved before the search runs`() = runTest {
        fake.enqueueJson("""{"ratings":[{"recipeId":"fav-1","isFavorite":true}]}""")
        fake.enqueueJson(PAGE)

        repository.search(null, RecipeFilters(favoritesOnly = true), page = 1)

        assertEquals("/api/users/self/favorites", fake.takeRequest().url.encodedPath)
        assertEquals("""id IN ["fav-1"]""", fake.takeRequest().query("queryFilter"))
    }

    @Test
    fun `a random sort carries the pagination seed Mealie requires`() = runTest {
        fake.enqueueJson(PAGE)
        repository.search(
            query = null,
            filters = RecipeFilters.None,
            page = 1,
            sort = RecipeSort(SortField.RANDOM, descending = true),
            paginationSeed = "12345",
        )
        val request = fake.takeRequest()
        assertEquals("random", request.query("orderBy"))
        assertEquals("12345", request.query("paginationSeed"))
    }

    @Test
    fun `the seed is not sent for a deterministic sort`() = runTest {
        fake.enqueueJson(PAGE)
        repository.search(
            query = null,
            filters = RecipeFilters.None,
            page = 1,
            sort = RecipeSort(SortField.NAME, descending = false),
            paginationSeed = "12345",
        )
        val request = fake.takeRequest()
        assertEquals(null, request.query("paginationSeed"))
        assertEquals("name", request.query("orderBy"))
        assertEquals("asc", request.query("orderDirection"))
    }

    @Test
    fun `a single recipe is fetched by slug and fully mapped`() = runTest {
        fake.enqueueJson(DETAIL)

        val result = repository.recipe("poulet-au-curry")

        assertTrue(result is ApiResult.Success)
        val recipe = (result as ApiResult.Success).value
        assertEquals("Poulet au curry", recipe.name)
        assertEquals(1, recipe.steps.size)
        assertEquals("/api/recipes/poulet-au-curry", fake.takeRequest().url.encodedPath)
    }

    @Test
    fun `a rejected token surfaces as an authentication error`() = runTest {
        fake.enqueueError(401)
        val result = repository.search(null, RecipeFilters.None, page = 1)
        assertEquals(NetworkError.Unauthorized, (result as ApiResult.Failure).error)
    }

    @Test
    fun `a missing recipe surfaces as not found`() = runTest {
        fake.enqueueError(404)
        val result = repository.recipe("inconnue")
        assertEquals(NetworkError.NotFound, (result as ApiResult.Failure).error)
    }

    @Test
    fun `a server failure keeps its status code`() = runTest {
        fake.enqueueError(503)
        val result = repository.search(null, RecipeFilters.None, page = 1)
        assertEquals(NetworkError.Server(503), (result as ApiResult.Failure).error)
    }

    @Test
    fun `a body that does not match the contract is reported as invalid`() = runTest {
        fake.enqueueJson("""{"items": "not-a-list"}""")
        val result = repository.search(null, RecipeFilters.None, page = 1)
        assertEquals(NetworkError.InvalidResponse, (result as ApiResult.Failure).error)
    }

    @Test
    fun `without a configured instance every call fails with unauthorized`() = runTest {
        val offline = RecipeRepository(apiProvider = { null })
        val result = offline.search(null, RecipeFilters.None, page = 1)
        assertEquals(NetworkError.Unauthorized, (result as ApiResult.Failure).error)
    }

    @Test
    fun `marking a favourite posts to the user endpoint`() = runTest {
        fake.enqueueJson("{}")
        val result = repository.setFavorite("poulet-au-curry", favorite = true)

        assertTrue(result is ApiResult.Success)
        val request = fake.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/users/user-1/favorites/poulet-au-curry", request.url.encodedPath)
    }

    @Test
    fun `removing a favourite deletes it`() = runTest {
        fake.enqueueJson("{}")
        repository.setFavorite("poulet-au-curry", favorite = false)
        assertEquals("DELETE", fake.takeRequest().method)
    }

    @Test
    fun `favourites cannot be changed without a user id`() = runTest {
        val tokenOnly = RecipeRepository(apiProvider = { fake.api() }, currentUserId = { null })
        val result = tokenOnly.setFavorite("x", favorite = true)
        assertEquals(NetworkError.Unauthorized, (result as ApiResult.Failure).error)
    }

    @Test
    fun `the reader's own rating is read for the recipe`() = runTest {
        fake.enqueueJson("""{"recipeId":"r1","rating":4.0,"isFavorite":false}""")

        val rating = repository.ownRating("r1")

        assertEquals(4, (rating as ApiResult.Success).value)
        assertEquals("/api/users/self/ratings/r1", fake.takeRequest().url.encodedPath)
    }

    @Test
    fun `a recipe the reader never rated has no rating rather than an error`() = runTest {
        fake.enqueueError(404)

        assertEquals(null, (repository.ownRating("r1") as ApiResult.Success).value)
    }

    @Test
    fun `rating sends the stars along with the favourite flag`() = runTest {
        fake.enqueueJson("null")

        val result = repository.setRating("poulet-au-curry", stars = 5, isFavorite = true)

        assertTrue(result is ApiResult.Success)
        val request = fake.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/users/user-1/ratings/poulet-au-curry", request.url.encodedPath)
        val body = request.body?.utf8().orEmpty()
        assertTrue(body.contains(""""rating":5.0"""))
        assertTrue(body.contains(""""isFavorite":true"""))
    }

    @Test
    fun `a rating cannot be given without a user id`() = runTest {
        val tokenOnly = RecipeRepository(apiProvider = { fake.api() }, currentUserId = { null })

        val result = tokenOnly.setRating("x", stars = 3, isFavorite = false)

        assertEquals(NetworkError.Unauthorized, (result as ApiResult.Failure).error)
        assertEquals(0, fake.server.requestCount)
    }

    @Test
    fun `a calorie range is filtered by Mealie through the calorie tags`() = runTest {
        val tagged = RecipeRepository(
            apiProvider = { fake.api() },
            calorieTags = { ApiResult.Success(listOf(CalorieTag("t1", "calorie-250", 250), CalorieTag("t2", "calorie-900", 900))) },
        )
        fake.enqueueJson(PAGE)

        tagged.search(null, RecipeFilters(calories = CalorieFilter.UP_TO_300), page = 1)

        assertEquals("""tags.slug IN ["calorie-250"]""", fake.takeRequest().query("queryFilter"))
    }

    @Test
    fun `recipes without calories are the ones without a calorie tag`() = runTest {
        val tagged = RecipeRepository(
            apiProvider = { fake.api() },
            calorieTags = { ApiResult.Success(listOf(CalorieTag("t1", "calorie-250", 250))) },
        )
        fake.enqueueJson(PAGE)

        tagged.search(null, RecipeFilters(calories = CalorieFilter.UNKNOWN), page = 1)

        assertEquals("""tags.slug NOT IN ["calorie-250"]""", fake.takeRequest().query("queryFilter"))
    }

    @Test
    fun `a cooked recipe gets a timeline entry and its last made date`() = runTest {
        fake.enqueueJson("""{"id":"e1"}""", code = 201)
        fake.enqueueJson("{}")
        val recipe = Recipe(
            summary = RecipeSummary(
                id = "r1", slug = "poulet", name = "Poulet", description = "", imageToken = null,
                servings = 0.0, yieldText = null, totalTime = null, prepTime = null, cookTime = null,
                performTime = null, categories = emptyList(), tags = emptyList(), tools = emptyList(),
                rating = null, sourceUrl = null, dateAdded = null, lastMade = null,
            ),
            ingredients = emptyList(), steps = emptyList(), nutrition = null, notes = emptyList(),
            showNutrition = false, showAssets = false, assets = emptyList(),
        )

        val result = repository.markCooked(recipe, "Recette cuisinée", Instant.parse("2026-09-23T18:00:00Z"))

        assertTrue(result is ApiResult.Success)
        val event = fake.takeRequest()
        assertEquals("/api/recipes/timeline/events", event.url.encodedPath)
        val body = event.body?.utf8().orEmpty()
        assertTrue(body.contains(""""recipeId":"r1""""))
        assertTrue(body.contains(""""subject":"Recette cuisinée""""))
        assertTrue(body.contains(""""eventType":"info""""))
        val lastMade = fake.takeRequest()
        assertEquals("PATCH", lastMade.method)
        assertEquals("/api/recipes/poulet/last-made", lastMade.url.encodedPath)
        assertTrue(lastMade.body?.utf8().orEmpty().contains("2026-09-23T18:00:00Z"))
    }

    private companion object {
        val PAGE = """
        {
          "page": 1, "per_page": 24, "total": 58, "total_pages": 3,
          "items": [
            {"id":"r1","name":"Poulet au curry","slug":"poulet-au-curry","image":"73",
             "recipeServings":1.0,"totalTime":"15 minutes","description":"Doux"},
            {"id":"r2","name":"Sans photo","slug":"sans-photo","image":null,"recipeServings":4.0}
          ],
          "next": "/recipes?page=2", "previous": null
        }
        """.trimIndent()

        val DETAIL = """
        {
          "id":"r1","name":"Poulet au curry","slug":"poulet-au-curry","image":"73",
          "recipeServings":1.0,
          "recipeIngredient":[{"quantity":1,"display":"1 Poulet","referenceId":"11111111-1111-4111-8111-111111111111"}],
          "recipeInstructions":[{"id":"s1","title":"","text":"Cuire.","ingredientReferences":[]}]
        }
        """.trimIndent()
    }
}
