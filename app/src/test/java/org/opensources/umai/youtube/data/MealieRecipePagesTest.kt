package org.opensources.umai.youtube.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.youtube.domain.RecipePage

class MealieRecipePagesTest {

    /** The site the description links to: a URL shortener and the recipe page behind it. */
    private val site = MockWebServer()
    private lateinit var mealie: FakeMealieServer

    @Before
    fun setUp() {
        site.start()
        mealie = FakeMealieServer()
    }

    @After
    fun tearDown() {
        site.close()
        mealie.shutdown()
    }

    private fun pages() = MealieRecipePages(apiProvider = { mealie.api() }, http = OkHttpClient())

    private fun redirectToPage(html: String) {
        site.enqueue(MockResponse.Builder().code(301).setHeader("Location", site.url("/salade-cesar/").toString()).build())
        site.enqueue(MockResponse.Builder().code(200).setHeader("Content-Type", "text/html; charset=utf-8").body(html).build())
    }

    @Test
    fun `the shortened link is followed and Mealie reads the recipe of the page`() = runBlocking {
        redirectToPage("<html><body>Recette</body></html>")
        mealie.enqueueJson(SCHEMA)

        val page = pages().read(site.url("/3xddjkdm").toString())

        val finalUrl = site.url("/salade-cesar/").toString()
        assertEquals(RecipePage(finalUrl, listOf("1 cœur de laitue romaine", "2 blancs de poulet", "½ citron"), servings = 6), page)
        val scrape = mealie.takeRequest()
        assertEquals("/api/recipes/test-scrape-url", scrape.url.encodedPath)
        // Mealie is asked for the page itself, not for the shortener.
        assertTrue(scrape.body?.utf8().orEmpty().contains(finalUrl))
    }

    @Test
    fun `a page whose recipe lists no ingredient is read from its ingredients heading`() = runBlocking {
        redirectToPage(CAESAR_PAGE)
        mealie.enqueueJson("""{"@type":"Recipe","name":"La salade César"}""")

        val page = pages().read(site.url("/3xddjkdm").toString())

        assertEquals(
            // "La salade :" is a paragraph, not an item of the list; the merge drops such headings anyway.
            listOf("1 cœur de laitue romaine", "1 gousse d’ail", "La sauce :", "1 gousse d’ail", "200ml d’huile d’olive"),
            page?.ingredients,
        )
        assertEquals(6, page?.servings)
    }

    @Test
    fun `a page without JSON-LD nor ingredients list gives no recipe`() = runBlocking {
        redirectToPage("<html><body><h1>Mon blog</h1><p>Bonjour</p><ul><li>Accueil</li></ul></body></html>")
        // Mealie answers with a sentence when it finds no recipe.
        mealie.enqueueJson(""""recipe_scrapers was unable to scrape this URL"""")

        assertNull(pages().read(site.url("/3xddjkdm").toString()))
    }

    @Test
    fun `a page that cannot be opened is still handed to Mealie`() = runBlocking {
        site.enqueue(MockResponse.Builder().code(503).build())
        mealie.enqueueJson(SCHEMA)

        val page = pages().read(site.url("/recette").toString())

        assertEquals(3, page?.ingredients?.size)
    }

    @Test
    fun `a link leading back to YouTube is not read`() = runBlocking {
        assertNull(pages().read("https://www.youtube.com/watch?v=abcdefghijk"))
        assertEquals(0, mealie.server.requestCount)
    }

    @Test
    fun `without an instance there is nothing to read with`() = runBlocking {
        redirectToPage(CAESAR_PAGE)

        assertNull(MealieRecipePages(apiProvider = { null }, http = OkHttpClient()).read(site.url("/3xddjkdm").toString()))
    }

    private companion object {
        val SCHEMA = """
            {"@context":"https://schema.org","@type":"Recipe","name":"La salade César",
             "recipeYield":["6","6 personnes"],
             "recipeIngredient":["1 cœur de laitue romaine","2 blancs  de poulet","&frac12; citron",""]}
        """.trimIndent()

        /** The shape of philippe-etchebest.com/salade-cesar: the list in the page, not in its Recipe. */
        val CAESAR_PAGE = """
            <html><head><script type="application/ld+json">{"@type":"Recipe","name":"La salade César"}</script>
            <script>var list = "<h3>Ingrédients</h3><ul><li>faux</li></ul>";</script></head><body>
            <h1>La salade César</h1>
            <h3 class="elementor-heading-title">Liste des ingrédients
            (pour 6 personnes) :</h3>
            <div><p><strong>La salade :</strong></p><ul><li>1 cœur de laitue romaine</li><li>1 gousse d&rsquo;ail</li></ul>
            <ul><li><strong>La sauce :</strong></li><li>1 gousse d’ail</li><li>200ml d’huile d’olive</li></ul></div>
            <h3>Préparation</h3><ul><li>Laver la salade.</li></ul>
            </body></html>
        """.trimIndent()
    }
}

class RecipePageParsingTest {

    private fun schema(json: String) = Json.parseToJsonElement(json) as JsonObject

    @Test
    fun `ingredients given as objects and a numeric yield are read`() {
        val page = RecipePageParsing.fromSchema(
            schema("""{"recipeIngredient":[{"text":"2 eggs"},{"name":"1 cup milk"}],"recipeYield":4}"""),
            "https://example.org/pancakes",
        )

        assertEquals(RecipePage("https://example.org/pancakes", listOf("2 eggs", "1 cup milk"), servings = 4), page)
    }

    @Test
    fun `a recipe without ingredients is no recipe`() {
        assertNull(RecipePageParsing.fromSchema(schema("""{"name":"Salade","recipeIngredient":[]}"""), "https://x"))
        assertNull(RecipePageParsing.fromSchema(null, "https://x"))
    }

    @Test
    fun `the equipment listed after the ingredients is left out`() {
        val html = """
            <h2>Ingrédients pour 8 personnes</h2>
            <ul><li>1 yaourt nature</li><li>2 œufs entiers</li><li>Matériel utilisé :</li><li>Fouet de cuisine inox</li></ul>
        """.trimIndent()

        assertEquals(listOf("1 yaourt nature", "2 œufs entiers"), RecipePageParsing.fromHtml(html, "https://x")?.ingredients)
    }

    @Test
    fun `a single item under the heading is not a list`() {
        assertNull(RecipePageParsing.fromHtml("<h2>Ingredients</h2><ul><li>Love</li></ul>", "https://x"))
    }
}
