package org.opensources.umai.provider.data

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.image.EncodedImage
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.provider.ProviderRegistry
import org.opensources.umai.provider.jow.JowProvider
import org.opensources.umai.recipe.data.RecipeMediaRepository

class ProviderMediaImporterTest {

    private lateinit var fake: FakeMealieServer
    private val downloaded = mutableListOf<String>()
    private lateinit var importer: ProviderMediaImporter

    @Before
    fun setUp() {
        fake = FakeMealieServer()
        importer = ProviderMediaImporter(
            apiProvider = { fake.api() },
            registry = ProviderRegistry(listOf(JowProvider)),
            media = RecipeMediaRepository { fake.api() },
            downloader = { url ->
                downloaded += url
                EncodedImage("photo".toByteArray(), mediaType = "image/jpeg", extension = "jpg")
            },
        )
    }

    @After
    fun tearDown() = fake.shutdown()

    @Test
    fun `a recipe just imported gets the chapters of its video and its step photos`() = runTest {
        fake.enqueueJson(recipe(assets = emptyList()))
        fake.enqueueJson(SCHEMA)
        fake.enqueueJson(ASSET)
        fake.enqueueJson(ASSET)

        val result = importer.import("carbonara")

        assertTrue(result is ApiResult.Success)
        fake.takeRequest()
        val scrape = fake.takeRequest()
        assertEquals("/api/recipes/test-scrape-url", scrape.url.encodedPath)
        assertTrue(scrape.body?.utf8().orEmpty().contains(SOURCE))

        val manifest = fake.takeRequest().body?.utf8().orEmpty()
        assertTrue(manifest.contains("carbonara-chapters"))
        assertTrue(manifest.contains("\"originalVideoUrl\":\"https://static.jow.fr/recipes/carbo.mp4\""))

        assertTrue(fake.takeRequest().body?.utf8().orEmpty().contains("step-1"))
        assertEquals(listOf("https://static.jow.fr/steps/1.jpg"), downloaded)
        assertEquals(4, fake.server.requestCount)
    }

    @Test
    fun `what the recipe already has is not written again`() = runTest {
        fake.enqueueJson(recipe(assets = listOf(CHAPTERS, "step-1.jpg")))
        fake.enqueueJson(SCHEMA)

        val result = importer.import("carbonara")

        assertTrue(result is ApiResult.Success)
        assertEquals(2, fake.server.requestCount)
        assertTrue(downloaded.isEmpty())
    }

    @Test
    fun `a page without a recipe adds nothing`() = runTest {
        fake.enqueueJson(recipe(assets = emptyList()))
        fake.enqueueJson("\"recipe_scrapers was unable to scrape this URL\"")

        val result = importer.import("carbonara")

        assertTrue(result is ApiResult.Success)
        assertEquals(2, fake.server.requestCount)
    }

    @Test
    fun `a recipe from another website is not a provider's business`() = runTest {
        fake.enqueueJson(recipe(assets = emptyList(), source = "https://www.marmiton.org/recettes/x.aspx"))

        val result = importer.import("carbonara")

        assertTrue(result is ApiResult.Success)
        assertEquals(1, fake.server.requestCount)
    }

    private companion object {
        const val SOURCE = "https://jow.fr/recipes/pates-carbonara-7vkm"
        const val CHAPTERS = "carbonara-chapters.json"

        fun recipe(assets: List<String>, source: String = SOURCE): String {
            val list = assets.joinToString(",") { """{"name":"${it.substringBeforeLast('.')}","icon":"","fileName":"$it"}""" }
            return """{"id":"r1","slug":"carbonara","name":"Carbonara","orgURL":"$source",
                "recipeInstructions":[{"id":"s1","text":"Coupez."},{"id":"s2","text":"Servez."}],
                "assets":[$list]}"""
        }

        const val ASSET = """{"name":"x","icon":"","fileName":"x"}"""

        val SCHEMA = """
            {"@type":"Recipe","name":"Carbonara",
             "video":[{"@type":"VideoObject","contentUrl":"https://static.jow.fr/recipes/carbo.mp4"}],
             "recipeInstructions":[
               {"@type":"HowToStep","text":"Coupez.","image":"https://static.jow.fr/steps/1.jpg",
                "video":{"@type":"Clip","startOffset":9.1,"endOffset":16.3}},
               {"@type":"HowToStep","text":"Servez.","video":{"@type":"Clip","startOffset":16.3}}]}
        """.trimIndent()
    }
}
