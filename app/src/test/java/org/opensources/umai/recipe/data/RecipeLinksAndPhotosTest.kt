package org.opensources.umai.recipe.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.image.EncodedImage
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.query
import org.opensources.umai.recipe.domain.DraftIngredient
import org.opensources.umai.recipe.domain.DraftStep
import org.opensources.umai.recipe.domain.RecipeDraft

/** Duplicates, links between ingredients and steps, and step photos, as sent to Mealie. */
class RecipeLinksAndPhotosTest {

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
    fun `a page already imported is found by its address`() = runTest {
        fake.enqueueJson(
            """{"page":1,"per_page":20,"total":2,"total_pages":1,"items":[
                {"id":"r1","slug":"autre","name":"Autre","orgURL":"https://jow.fr/recipes/curry-plus"},
                {"id":"r2","slug":"curry","name":"Curry","orgURL":"https://jow.fr/recipes/curry/"}]}""",
        )

        val found = (repository.findBySource("https://www.jow.fr/recipes/curry?utm=1") as ApiResult.Success).value

        assertEquals("curry", found?.slug)
        assertEquals("""orgURL LIKE "%jow.fr/recipes/curry%"""", fake.takeRequest().query("queryFilter"))
    }

    @Test
    fun `a new page is not a duplicate`() = runTest {
        fake.enqueueJson("""{"page":1,"per_page":20,"total":0,"total_pages":0,"items":[]}""")

        assertNull((repository.findBySource("https://jow.fr/recipes/nouveau") as ApiResult.Success).value)
    }

    @Test
    fun `a created recipe carries the links of its steps`() = runTest {
        fake.enqueueJson(""""gratin"""")
        fake.enqueueJson(CREATED)
        fake.enqueueJson(CREATED)

        repository.create(
            RecipeDraft(
                id = "d1",
                name = "Gratin",
                ingredients = listOf(DraftIngredient("Courgettes", "ref-c"), DraftIngredient("", "ref-empty")),
                steps = listOf(DraftStep(text = "Couper les courgettes.", ingredientReferences = listOf("ref-c", "ref-empty"))),
            ),
        )

        repeat(2) { fake.takeRequest() }
        val body = fake.takeRequest().body?.utf8().orEmpty()
        assertTrue(body.contains(""""referenceId":"ref-c""""))
        // A link to an empty line, which is not written, is dropped with it.
        assertFalse(body.contains("ref-empty"))
    }

    @Test
    fun `saving writes the links of the steps and the reference of every line`() = runTest {
        fake.enqueueJson(EXISTING)
        val original = (repository.loadForEdit("tarte") as ApiResult.Success).value.draft
        fake.takeRequest()
        fake.enqueueJson(EXISTING)
        fake.enqueueJson(EXISTING)
        val added = DraftIngredient("Beurre", "ref-new")
        val edited = original.copy(
            ingredients = original.ingredients + added,
            steps = original.steps.map { it.copy(ingredientReferences = it.ingredientReferences + "ref-2" + "ref-new") },
        )

        repository.update("tarte", original, edited)

        fake.takeRequest()
        val body = fake.takeRequest().body?.utf8().orEmpty()
        val instructions = body.substringAfter(""""recipeInstructions":""")
        assertTrue(instructions.contains("""{"referenceId":"ref-1"},{"referenceId":"ref-2"},{"referenceId":"ref-new"}"""))
        assertTrue(body.contains(""""note":"Beurre""""))
        assertTrue(body.contains(""""referenceId":"ref-new""""))
        // The line left as it was keeps its food.
        assertTrue(body.contains(""""food":{"id":"f1","name":"farine"}"""))
    }

    @Test
    fun `only new photos changes nothing in the recipe itself`() = runTest {
        fake.enqueueJson(EXISTING)
        val original = RecipeDraft(id = "tarte", name = "Tarte", steps = listOf(DraftStep(text = "Mélanger", id = "s1")))
        val edited = original.copy(steps = listOf(original.steps[0].copy(photoPath = "/tmp/p.jpg")))

        assertEquals("tarte", (repository.update("tarte", original, edited) as ApiResult.Success).value)
        // The document was read, found unchanged, and not sent back.
        assertEquals(1, fake.server.requestCount)
    }

    @Test
    fun `a new step photo is stored under the number of its step`() = runTest {
        fake.enqueueJson(ASSET)
        fake.enqueueJson(EXISTING)
        val original = RecipeDraft(id = "tarte", name = "Tarte", steps = listOf(DraftStep(text = "Un"), DraftStep(text = "Deux")))
        val edited = original.copy(steps = listOf(original.steps[0], original.steps[1].copy(photoPath = "/tmp/p.jpg")))

        val result = repository.saveStepPhotos("tarte", "r1", original, edited, mapOf(2 to PHOTO))

        assertTrue(result is ApiResult.Success)
        val upload = fake.takeRequest()
        assertEquals("/api/recipes/tarte/assets", upload.url.encodedPath)
        val body = upload.body?.utf8().orEmpty()
        assertTrue(body.contains("step-2"))
        assertTrue(body.contains("""filename="step-2.jpg""""))
    }

    @Test
    fun `removing a step moves the photos of the following ones`() {
        val original = RecipeDraft(
            id = "tarte",
            name = "Tarte",
            steps = listOf(
                DraftStep(text = "Un", photoFile = "step-1.jpg"),
                DraftStep(text = "Deux", photoFile = "step-2.jpg"),
                DraftStep(text = "Trois", photoFile = "step-3.png"),
            ),
        )
        val edited = original.copy(steps = listOf(original.steps[0], original.steps[2]))

        val plan = StepPhotoPlan.of(original, edited, newPhotos = emptyMap())

        assertEquals(mapOf(2 to "step-3.png"), plan.moves)
        assertEquals(setOf("step-1.jpg", "step-2.png"), plan.kept)
        assertFalse(plan.isEmpty)
    }

    @Test
    fun `steps left in place need no photo work`() {
        val draft = RecipeDraft(id = "tarte", name = "Tarte", steps = listOf(DraftStep(text = "Un", photoFile = "step-1.jpg")))

        assertTrue(StepPhotoPlan.of(draft, draft.copy(name = "Tarte fine"), emptyMap()).isEmpty)
    }

    @Test
    fun `drafts written before ingredient references still open`() {
        val json = Json { ignoreUnknownKeys = true }

        val draft = json.decodeFromString(
            RecipeDraft.serializer(),
            """{"id":"d1","name":"Tarte","ingredients":["200 g farine","Sel"],"steps":[{"text":"Mélanger"}]}""",
        )

        assertEquals(listOf("200 g farine", "Sel"), draft.ingredients.map { it.text })
        assertTrue(draft.ingredients.all { it.referenceId.isNotBlank() })
        assertEquals(2, draft.ingredients.map { it.referenceId }.distinct().size)
    }

    private companion object {
        val PHOTO = EncodedImage("jpeg".toByteArray(), mediaType = "image/jpeg", extension = "jpg")
        const val ASSET = """{"name":"step-2","icon":"file-image","fileName":"step-2.jpg"}"""

        val EXISTING = """
            {"id":"r1","name":"Tarte","slug":"tarte",
             "recipeIngredient":[
               {"quantity":200,"food":{"id":"f1","name":"farine"},"note":"","display":"200 g farine","referenceId":"ref-1"},
               {"quantity":0,"note":"1 pincée de sel","display":"1 pincée de sel","referenceId":"ref-2"}
             ],
             "recipeInstructions":[{"id":"s1","title":"","text":"Mélanger","ingredientReferences":[{"referenceId":"ref-1"}]}],
             "assets":[{"name":"step-2","icon":"file-image","fileName":"step-2.jpg"}]}
        """.trimIndent()

        val CREATED = """{"id":"r1","name":"Gratin","slug":"gratin","recipeIngredient":[],"recipeInstructions":[]}"""
    }
}
