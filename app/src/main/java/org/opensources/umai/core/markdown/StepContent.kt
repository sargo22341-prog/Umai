package org.opensources.umai.core.markdown

/**
 * Mealie stores recipe instructions as Markdown and has no image field on a
 * step: pictures added from the web UI end up inside the step text, either as
 * `![alt](src)` or as a raw `<img src="...">` tag pointing at a recipe asset.
 *
 * [StepContent.parse] pulls those references out so the cooking mode can render
 * real images instead of showing markup, and returns the remaining prose.
 */
data class StepContent(
    val text: String,
    val imageSources: List<String>,
) {
    companion object {

        private val markdownImage = Regex("""!\[([^\]]*)]\(\s*<?([^)\s>]+)>?(?:\s+"[^"]*")?\s*\)""")
        private val htmlImage = Regex("""<img\b[^>]*?\bsrc\s*=\s*["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
        private val blankLines = Regex("""\n{3,}""")

        fun parse(raw: String): StepContent {
            if (raw.isBlank()) return StepContent("", emptyList())

            val sources = mutableListOf<String>()
            var stripped = markdownImage.replace(raw) { match ->
                sources += match.groupValues[2].trim()
                ""
            }
            stripped = htmlImage.replace(stripped) { match ->
                sources += match.groupValues[1].trim()
                ""
            }

            val text = stripped
                .replace(blankLines, "\n\n")
                .lines()
                .joinToString("\n") { it.trimEnd() }
                .trim()

            return StepContent(text, sources.filter { it.isNotBlank() }.distinct())
        }
    }
}
