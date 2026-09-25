package org.opensources.umai.recipe.domain

import org.opensources.umai.youtube.domain.YouTubeLinks
import java.net.URI

/** Web addresses of recipe pages, as they are shared and as Mealie stores them. */
object RecipeLinks {

    private val webAddress = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private val trailingPunctuation = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"')

    /**
     * The address in a shared text. Apps share a sentence around the link
     * ("Découvre cette recette : https://…"), so the first web address is kept.
     */
    fun extract(sharedText: String?): String? =
        sharedText?.let(webAddress::find)?.value?.trimEnd(*trailingPunctuation)?.takeIf { it.length > "https://".length }

    /**
     * Two addresses of the same page: the scheme, a leading `www.`, the query,
     * the fragment and a trailing slash make no difference. A YouTube video is
     * the exception: its query is the video, whatever form the address takes.
     */
    fun sameSource(first: String, second: String): Boolean {
        val a = key(first) ?: return false
        return a == key(second)
    }

    /** A fragment of the address specific enough to look it up with `LIKE`. */
    fun searchFragment(url: String): String? = key(url)?.takeIf { '"' !in it && '\\' !in it }

    private fun key(url: String): String? = YouTubeLinks.videoId(url)?.let { "youtube.com/watch?v=$it" } ?: runCatching {
        val uri = URI(url.trim())
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        val path = uri.rawPath.orEmpty().trimEnd('/')
        "$host$path"
    }.getOrNull()
}
