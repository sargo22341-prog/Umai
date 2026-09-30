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
 * the page itself. It is written by whoever posted the video: only a page the
 * app could reach itself — never on the local network (see [http]) — goes on
 * to Mealie. The page is then read by Mealie's own scraper, through
 * `POST /api/recipes/test-scrape-url`, which answers with the schema.org
 * Recipe it finds and creates nothing on the instance. Some sites publish a
 * Recipe with no ingredient in it and write the list in the page only: the
 * list under the page's "Ingredients" heading is then read instead.
 */
class MealieRecipePages(
    private val apiProvider: () -> MealieApi?,
    /**
     * The client for other websites, which never carries Mealie credentials
     * and refuses the local network (see [org.opensources.umai.core.network.PublicAddressGuard]).
     */
    private val http: OkHttpClient,
) : RecipePageSource {

    override suspend fun read(url: String): RecipePage? {
        if (YouTubeLinks.isYouTube(url)) return null
        val page = fetch(url) ?: return null
        if (YouTubeLinks.isYouTube(page.url)) return null
        val api = apiProvider() ?: return null
        // Mealie answers with a sentence when it finds no recipe on the page.
        val schema = apiCall { api.testScrapeUrl(ScrapeRecipeTestDto(page.url)) }.valueOrNull() as? JsonObject
        return RecipePageParsing.fromSchema(schema, page.url)
            ?: page.html?.let { RecipePageParsing.fromHtml(it, page.url) }
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
            // Unreachable, or on the local network.
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
        val body = withoutHidden(html)
        val headings = elements(body, heading)
        headings.forEachIndexed { index, match ->
            val title = cleanText(match.content)
            if (!VideoDescription.isIngredientHeading(title)) return@forEachIndexed
            val end = headings.getOrNull(index + 1)?.start ?: body.length
            val items = elements(body, listItem, from = match.end, until = end)
                .map { cleanText(it.content) }
                // The equipment is often listed right after, under a sub-heading of the same list.
                .takeWhile { !(it.endsWith(":") && otherList.containsMatchIn(VideoDescription.fold(it))) }
                .filter { it.isNotEmpty() && it.length <= MAX_ITEM_LENGTH }
            if (items.size >= MIN_ITEMS) {
                return RecipePage(url = url, ingredients = items, servings = VideoDescription.servings(title))
            }
        }
        return null
    }

    /** An element of a page: where its opening tag starts, its content, and where its closing tag ends. */
    private class Element(val start: Int, val content: String, val end: Int)

    /**
     * The elements [open] starts between [from] and [until], each up to its
     * first closing tag, one after the other; an element left unclosed is
     * skipped. A regular expression spanning a whole element would read the
     * rest of the page again for every tag left unclosed: here a closing tag
     * found missing is not looked for twice, and the time stays linear in the
     * length of the page, however it is written.
     */
    private fun elements(html: String, open: Regex, from: Int = 0, until: Int = html.length): List<Element> {
        val found = mutableListOf<Element>()
        val unclosed = mutableSetOf<String>()
        var at = from
        while (at < until) {
            val tag = open.find(html, at)?.takeIf { it.range.first < until } ?: break
            val name = tag.groupValues[1].lowercase()
            val tagEnd = html.indexOf('>', tag.range.last + 1)
            if (tagEnd < 0 || tagEnd >= until) break
            val close = if (name in unclosed) null else closingTag(html, name, tagEnd + 1, until)
            if (close == null) {
                unclosed += name
                at = tagEnd + 1
                continue
            }
            found += Element(tag.range.first, html.substring(tagEnd + 1, close.first), close.last + 1)
            at = close.last + 1
        }
        return found
    }

    /** Where `</name>` first stands between [from] and [until], spaces allowed before its `>`. */
    private fun closingTag(html: String, name: String, from: Int, until: Int): IntRange? {
        var at = from
        while (at < until) {
            val start = html.indexOf("</$name", at, ignoreCase = true)
            if (start < 0 || start >= until) return null
            var end = start + name.length + 2
            while (end < until && html[end].isWhitespace()) end++
            if (end < until && html[end] == '>') return start..end
            at = start + 1
        }
        return null
    }

    /** [html] without its scripts, styles and templates, which hold no text of the page. */
    private fun withoutHidden(html: String): String {
        val text = StringBuilder(html.length)
        var at = 0
        elements(html, hidden).forEach { element ->
            text.append(html, at, element.start).append(' ')
            at = element.end
        }
        return text.append(html, at, html.length).toString()
    }

    /** The texts of a value: itself, or those of a list, whose objects give their `text` or `name`. */
    private fun JsonElement?.strings(): List<String> {
        val found = mutableListOf<String>()
        // Depth first, in the order of the page: the next node to read is on top.
        val pending = ArrayDeque<JsonElement>()
        this?.let(pending::addLast)
        while (pending.isNotEmpty()) {
            when (val node = pending.removeLast()) {
                is JsonPrimitive -> node.contentOrNull?.let(found::add)
                is JsonArray -> node.asReversed().forEach { item ->
                    val text = if (item is JsonObject) item["text"] ?: item["name"] else item
                    text?.let(pending::addLast)
                }
                // An object only gives a text as the item of a list.
                is JsonObject -> Unit
            }
        }
        return found
    }

    /** "4", "4 personnes", ["4", "4 servings"]: the first number of servings given. */
    private fun servings(yield: JsonElement?): Int? = yield.strings().firstNotNullOfOrNull { text ->
        firstNumber.find(text)?.value?.toIntOrNull()?.takeIf { it in 1..MAX_SERVINGS }
    }

    /** Text out of markup: tags dropped, character references decoded, spaces collapsed. */
    private fun cleanText(markup: String): String =
        YouTubeMarkup.decode(markup.replace(tag, " ")).replace(spaces, " ").trim()

    // Opening tags only: elements() reads each element to its end, in linear time.
    private val hidden = Regex("""<(script|style|noscript|template)\b""", RegexOption.IGNORE_CASE)
    private val heading = Regex("""<(h[1-6])\b""", RegexOption.IGNORE_CASE)
    private val listItem = Regex("""<(li)\b""", RegexOption.IGNORE_CASE)
    private val tag = Regex("""<[^>]+>""")
    private val spaces = Regex("""\s+""")
    private val firstNumber = Regex("""\d+""")
    private val otherList = Regex("""^(?:materiel|ustensile|equipement|equipment|tools?|utensils|gear)\b""")

    private const val MIN_ITEMS = 2
    private const val MAX_ITEM_LENGTH = 150
    private const val MAX_SERVINGS = 50
}
