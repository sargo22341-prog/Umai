package org.opensources.umai.youtube.data

import org.opensources.umai.youtube.domain.TranscriptCue

/**
 * Turns what NewPipeExtractor hands over as markup into what the recipe is
 * rebuilt from: the description, which it gives as HTML, and the captions,
 * which YouTube serves as TTML. Its character references also serve the
 * recipe pages a description links to.
 *
 * Both documents are machine-written by YouTube, in a narrow and stable
 * shape: a few patterns read them, with no general HTML or XML parser.
 */
internal object YouTubeMarkup {

    /**
     * The description as the author typed it. A link shows its full address,
     * since YouTube shortens the address it displays ("https://site.com/rec..."),
     * and the recipe links are followed from here.
     */
    fun descriptionText(html: String): String {
        val withLinks = link.replace(html) { match ->
            val href = decode(match.groupValues[1])
            val label = decode(tag.replace(match.groupValues[2], "")).trim()
            if (label.startsWith("http") || label.startsWith("www.")) unwrapRedirect(href) else label
        }
        val text = withLinks.replace(lineBreak, "\n").replace(tag, "")
        return decode(text).lines().joinToString("\n") { it.trimEnd() }.trim()
    }

    /** The cues of a TTML caption track, in seconds; empty when it holds none. */
    fun ttmlCues(ttml: String): List<TranscriptCue> =
        paragraph.findAll(ttml).mapNotNull { match ->
            val start = clockSeconds(match.groupValues[1]) ?: return@mapNotNull null
            val end = clockSeconds(match.groupValues[2]) ?: start
            val words = decode(match.groupValues[3].replace(lineBreak, " ").replace(tag, ""))
                .replace(spaces, " ")
                .trim()
            words.takeIf { it.isNotEmpty() }?.let { TranscriptCue(start, end, it) }
        }.toList()

    /** "00:01:02.500" as seconds. */
    private fun clockSeconds(text: String): Double? {
        val parts = text.trim().split(':')
        if (parts.size != 3) return null
        val hours = parts[0].toIntOrNull() ?: return null
        val minutes = parts[1].toIntOrNull() ?: return null
        val seconds = parts[2].toDoubleOrNull() ?: return null
        return hours * 3600 + minutes * 60 + seconds
    }

    /** YouTube's `redirect?q=` wrapper around an outside link, removed. */
    private fun unwrapRedirect(href: String): String {
        if (!href.contains("youtube.com/redirect")) return href
        val target = href.substringAfter("?").split('&')
            .firstOrNull { it.startsWith("q=") }
            ?.removePrefix("q=")
            ?: return href
        return runCatching { java.net.URLDecoder.decode(target, Charsets.UTF_8) }.getOrDefault(href)
    }

    /** The character references HTML and XML use: named ones YouTube writes, and numeric ones. */
    fun decode(text: String): String = entity.replace(text) { match ->
        val name = match.groupValues[1]
        when {
            name.startsWith("#x", ignoreCase = true) -> name.drop(2).toIntOrNull(16)?.let(::codePoint)
            name.startsWith("#") -> name.drop(1).toIntOrNull()?.let(::codePoint)
            else -> namedEntities[name]
        } ?: match.value
    }

    private fun codePoint(value: Int): String? =
        value.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) }

    private val link = Regex("""<a\s[^>]*?href="([^"]*)"[^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val lineBreak = Regex("""\s*<br\s*/?>\s?""", RegexOption.IGNORE_CASE)
    private val tag = Regex("""<[^>]+>""")
    private val spaces = Regex("""\s+""")
    private val entity = Regex("""&(#x[0-9a-fA-F]+|#\d+|[a-zA-Z][a-zA-Z0-9]*);""")
    private val paragraph = Regex(
        """<p\b[^>]*?\bbegin="([^"]+)"[^>]*?\bend="([^"]+)"[^>]*>(.*?)</p>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    /** The named references YouTube writes, and those recipe pages commonly use for amounts and French text. */
    private val namedEntities = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "frac12" to "½", "frac14" to "¼", "frac34" to "¾", "deg" to "°", "times" to "×",
        "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“", "laquo" to "«", "raquo" to "»",
        "hellip" to "…", "ndash" to "–", "mdash" to "—",
        "eacute" to "é", "egrave" to "è", "ecirc" to "ê", "agrave" to "à", "acirc" to "â", "ccedil" to "ç",
        "icirc" to "î", "iuml" to "ï", "ocirc" to "ô", "ucirc" to "û", "ugrave" to "ù", "oelig" to "œ",
    )
}
