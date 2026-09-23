package org.opensources.umai.recipe.data

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.image.EncodedImage
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.recipe.domain.DraftIngredient
import org.opensources.umai.recipe.domain.DraftOrganizer
import org.opensources.umai.recipe.domain.DraftStep
import org.opensources.umai.recipe.domain.RecipeDraft

class RecipeEditRepositoryTest {

    private lateinit var fake: FakeMealieServer
    private lateinit var repository: RecipeEditRepository

    @Before
    fun setUp() {
        fake = FakeMealieServer()
        repository = RecipeEditRepository(apiProvider = { fake.api() })
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
                ingredients = listOf("2 courgettes", "  ", "Creme").map { DraftIngredient(it) },
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
                ingredients = listOf("Sel", "   ", "").map { DraftIngredient(it) },
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

    @Test
    fun `a created recipe then receives its picture`() = runTest {
        fake.enqueueJson(""""gratin"""")
        fake.enqueueJson(CREATED_RECIPE)
        fake.enqueueJson(CREATED_RECIPE)
        fake.enqueueJson("""{"image":"new-token"}""")

        val result = repository.create(RecipeDraft(id = "d1", name = "Gratin"), IMAGE)

        assertEquals(CreatedRecipe("gratin-de-courgettes", imageSaved = true), (result as ApiResult.Success).value)
        repeat(3) { fake.takeRequest() }
        val upload = fake.takeRequest()
        assertEquals("PUT", upload.method)
        assertEquals("/api/recipes/gratin-de-courgettes/image", upload.url.encodedPath)
        val body = upload.body?.utf8().orEmpty()
        assertTrue(body.contains("""name="image"; filename="recipe.jpg""""))
        assertTrue(body.contains("""name="extension""""))
    }

    @Test
    fun `a refused picture does not undo the recipe`() = runTest {
        fake.enqueueJson(""""gratin"""")
        fake.enqueueJson(CREATED_RECIPE)
        fake.enqueueJson(CREATED_RECIPE)
        fake.enqueueError(500)

        val result = repository.create(RecipeDraft(id = "d1", name = "Gratin"), IMAGE)

        assertEquals(CreatedRecipe("gratin-de-courgettes", imageSaved = false), (result as ApiResult.Success).value)
    }

    @Test
    fun `an existing recipe opens in the form as it reads`() = runTest {
        fake.enqueueJson(EXISTING)

        val editable = (repository.loadForEdit("tarte") as ApiResult.Success).value

        assertEquals("r1", editable.recipeId)
        assertEquals("tok", editable.imageToken)
        val draft = editable.draft
        assertEquals("Tarte", draft.name)
        assertEquals(6, draft.servings)
        assertEquals("45 minutes", draft.cookTime)
        assertEquals(listOf("200 g farine", "1 pincée de sel"), draft.ingredients.map { it.text })
        assertEquals(listOf("ref-1", "ref-2"), draft.ingredients.map { it.referenceId })
        assertEquals("farine", draft.ingredients.first().food?.name)
        assertEquals(listOf("ref-1"), draft.steps.single().ingredientReferences)
        assertEquals("s1", draft.steps.single().id)
        assertEquals("Mélanger ![](/api/media/recipes/r1/assets/a.jpg)", draft.steps.single().text)
        assertEquals(listOf("c1"), draft.categories.map { it.id })
    }

    @Test
    fun `saving writes only what changed and keeps everything else`() = runTest {
        fake.enqueueJson(EXISTING)
        val original = (repository.loadForEdit("tarte") as ApiResult.Success).value.draft
        fake.takeRequest()

        fake.enqueueJson(EXISTING)
        fake.enqueueJson(EXISTING)
        val edited = original.copy(
            description = "Nouvelle",
            ingredients = listOf(original.ingredients[0], original.ingredients[1].copy(text = "2 pincées de sel")),
            steps = original.steps.map { it.copy(text = "Bien mélanger") },
        )

        val result = repository.update("tarte", original, edited)

        assertEquals("tarte", (result as ApiResult.Success).value)
        assertEquals("GET", fake.takeRequest().method)
        val put = fake.takeRequest()
        assertEquals("PUT", put.method)
        val body = put.body?.utf8().orEmpty()
        assertTrue(body.contains(""""description":"Nouvelle""""))
        // The untouched line keeps its structured food; the edited one becomes a note.
        assertTrue(body.contains(""""food":{"id":"f1","name":"farine"}"""))
        assertTrue(body.contains(""""note":"2 pincées de sel""""))
        // The step keeps its id and the ingredient linked to it.
        assertTrue(body.contains(""""id":"s1""""))
        assertTrue(body.contains(""""referenceId":"ref-1""""))
        assertTrue(body.contains(""""text":"Bien mélanger""""))
        // Fields Umai does not model come back untouched.
        assertTrue(body.contains(""""extras":{"source":"family"}"""))
        assertTrue(body.contains(""""name":"Tarte""""))
        assertTrue(body.contains(""""performTime":"45 minutes""""))
    }

    @Test
    fun `nothing changed means nothing is sent`() = runTest {
        val draft = RecipeDraft(id = "tarte", name = "Tarte")

        val result = repository.update("tarte", draft, draft)

        assertEquals("tarte", (result as ApiResult.Success).value)
        assertEquals(0, fake.server.requestCount)
    }

    @Test
    fun `renaming answers with the new slug`() = runTest {
        fake.enqueueJson(EXISTING)
        fake.enqueueJson(EXISTING.replace(""""slug":"tarte"""", """"slug":"tarte-fine""""))
        val original = RecipeDraft(id = "tarte", name = "Tarte")

        val result = repository.update("tarte", original, original.copy(name = "Tarte fine"))

        assertEquals("tarte-fine", (result as ApiResult.Success).value)
    }

    @Test
    fun `a recipe cannot be saved without a name`() = runTest {
        val original = RecipeDraft(id = "tarte", name = "Tarte")

        val result = repository.update("tarte", original, original.copy(name = "  "))

        assertEquals(NetworkError.InvalidResponse, (result as ApiResult.Failure).error)
        assertEquals(0, fake.server.requestCount)
    }

    private companion object {
        val IMAGE = EncodedImage("jpeg".toByteArray(), mediaType = "image/jpeg", extension = "jpg")

        val EXISTING = """
            {"id":"r1","name":"Tarte","slug":"tarte","image":"tok","description":"Ancienne",
             "recipeServings":6,"performTime":"45 minutes","extras":{"source":"family"},
             "recipeIngredient":[
               {"quantity":200,"unit":{"id":"u1","name":"g"},"food":{"id":"f1","name":"farine"},
                "note":"","display":"200 g farine","referenceId":"ref-1"},
               {"quantity":0,"note":"1 pincée de sel","display":"1 pincée de sel","referenceId":"ref-2"}
             ],
             "recipeInstructions":[
               {"id":"s1","title":"","text":"Mélanger ![](/api/media/recipes/r1/assets/a.jpg)",
                "ingredientReferences":[{"referenceId":"ref-1"}]}
             ],
             "recipeCategory":[{"id":"c1","name":"Dessert","slug":"dessert"}],
             "tags":[]}
        """.trimIndent()

        val CREATED_RECIPE = """
            {"id":"r1","name":"Gratin de courgettes","slug":"gratin-de-courgettes",
             "recipeIngredient":[],"recipeInstructions":[]}
        """.trimIndent()
    }
}
