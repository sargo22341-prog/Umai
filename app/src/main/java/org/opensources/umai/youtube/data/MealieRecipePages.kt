package org.opensources.umai.youtube.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.opensources.umai.core.network.api.MealieApi
import org.opensources.umai.core.network.apiCall
import org.opensources.umai.core.network.dto.ScrapeRecipeTestDto
import org.opensources.umai.core.network.valueOrNull
import org.opensources.umai.youtube.domain.RecipePage
import org.opensources.umai.youtube.domain.RecipePageSource
import org.opensources.umai.youtube.domain.VideoDescription
import org.opensources.umai.youtube.domain.YouTubeLinks
import java.io.IOException

/**
 * Reads the recipe page a video description links to.
 *
 * The link is opened first, to follow the redirections of URL shorteners to
 * the page itself. The page is then read by Mealie's own scraper, through
 * `POST /api/recipes/test-scrape-url`, which answers with the schema.org
 * Recipe it finds and creates nothing on the instance. Some sites publish a
 * Recipe with no ingredient in it and write the list in the page only: the
 * list under the page's "Ingredients" heading is then read instead.
 */
class MealieRecipePages(
    private val apiProvider: () -> MealieApi?,
    /** The client for other websites, which never carries Mealie credentials. */
    private val http: OkHttpClient,
) : RecipePageSource {

    override suspend fun read(url: String): RecipePage? {
        val page = fetch(url)
        val address = page?.url ?: url
        if (YouTubeLinks.isYouTube(address)) return null
        val api = apiProvider() ?: return null
        // Mealie answers with a sentence when it finds no recipe on the page.
        val schema = apiCall { api.testScrapeUrl(ScrapeRecipeTestDto(address)) }.valueOrNull() as? JsonObject
        return RecipePageParsing.fromSchema(schema, address)
            ?: page?.html?.let { RecipePageParsing.fromHtml(it, address) }
    }

    private class Fetched(val url: String, val html: String?)

    private suspend fun fetch(url: String): Fetched? = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val html = response.takeIf { it.isSuccessful && it.body.contentType()?.subtype == "html" }
                    ?.peekBody(MAX_PAGE_BYTES)
                    ?.string()
                Fetched(response.request.url.toString(), html)
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            // Not an address OkHttp can request.
            null
        }
    }

    private companion object {
        const val MAX_PAGE_BYTES = 3L * 1024 * 1024
    }
}

/** Reads a recipe out of what a page publishes. */
internal object RecipePageParsing {

    /** The recipe schema.org describes, `null` when it lists no ingredient. */
    fun fromSchema(schema: JsonObject?, url: String): RecipePage? {
        schema ?: return null
        val ingredients = (schema["recipeIngredient"] ?: schema["ingredients"]).strings()
            .map(::cleanText)
            .filter { it.isNotEmpty() }
        if (ingredients.isEmpty()) return null
        return RecipePage(url = url, ingredients = ingredients, servings = servings(schema["recipeYield"]))
    }

    /**
     * The list under the page's ingredients heading: its list items, up to
     * the next heading. `null` when no heading announces ingredients, or
     * fewer than [MIN_ITEMS] items follow it.
     */
    fun fromHtml(html: String, url: String): RecipePage? {
        val body = html.replace(hidden, " ")
        val headings = heading.findAll(body).toList()
        headings.forEachIndexed { index, match ->
            val title = cleanText(match.groupValues[2])
            if (!VideoDescription.isIngredientHeading(title)) return@forEachIndexed
            val end = headings.getOrNull(index + 1)?.range?.first ?: body.length
            val items = listItem.findAll(body.substring(match.range.last + 1, end))
                .map { cleanText(it.groupValues[1]) }
                // The equipment is often listed right after, under a sub-heading of the same list.
                .takeWhile { !(it.endsWith(":") && otherList.containsMatchIn(VideoDescription.fold(it))) }
                .filter { it.isNotEmpty() && it.length <= MAX_ITEM_LENGTH }
                .toList()
            if (items.size >= MIN_ITEMS) {
                return RecipePage(url = url, ingredients = items, servings = VideoDescription.servings(title))
            }
        }
        return null
    }

    private fun JsonElement?.strings(): List<String> = when (this) {
        is JsonPrimitive -> listOfNotNull(contentOrNull)
        is JsonArray -> flatMap { item ->
            when (item) {
                is JsonObject -> (item["text"] ?: item["name"]).strings()
                else -> item.strings()
            }
        }
        else -> emptyList()
    }

    /** "4", "4 personnes", ["4", "4 servings"]: the first number of servings given. */
    private fun servings(yield: JsonElement?): Int? = yield.strings().firstNotNullOfOrNull { text ->
        firstNumber.find(text)?.value?.toIntOrNull()?.takeIf { it in 1..MAX_SERVINGS }
    }

    /** Text out of markup: tags dropped, character references decoded, spaces collapsed. */
    private fun cleanText(markup: String): String =
        YouTubeMarkup.decode(markup.replace(tag, " ")).replace(spaces, " ").trim()

    private val hidden = Regex("""<(script|style|noscript|template)\b.*?</\1\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val heading = Regex("""<h([1-6])\b[^>]*>(.*?)</h\1\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val listItem = Regex("""<li\b[^>]*>(.*?)</li\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val tag = Regex("""<[^>]+>""")
    private val spaces = Regex("""\s+""")
    private val firstNumber = Regex("""\d+""")
    private val otherList = Regex("""^(?:materiel|ustensile|equipement|equipment|tools?|utensils|gear)\b""")

    private const val MIN_ITEMS = 2
    private const val MAX_ITEM_LENGTH = 150
    private const val MAX_SERVINGS = 50
}
