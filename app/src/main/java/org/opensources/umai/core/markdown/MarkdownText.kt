package org.opensources.umai.core.markdown

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * Minimal inline Markdown renderer for recipe prose: bold, italic, inline code
 * and links. Block level constructs are left as plain lines, which is enough
 * for Mealie instructions, notes and descriptions and keeps the app free of a
 * heavyweight Markdown dependency.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    val linkStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
    )
    val annotated = remember(markdown, linkStyle) { InlineMarkdown.annotate(markdown, linkStyle) }
    Text(text = annotated, modifier = modifier, style = style)
}

internal object InlineMarkdown {

    private val token = Regex(
        """\*\*(.+?)\*\*|__(.+?)__|(?<![*\w])\*(?!\s)(.+?)(?<!\s)\*(?![*\w])|(?<!\w)_(?!\s)(.+?)(?<!\s)_(?!\w)|`([^`]+)`|\[([^\]]+)]\(([^)\s]+)\)""",
        RegexOption.DOT_MATCHES_ALL,
    )

    fun annotate(markdown: String, linkStyle: SpanStyle = SpanStyle()): AnnotatedString =
        buildAnnotatedString {
            val source = markdown.trim()
            var cursor = 0
            token.findAll(source).forEach { match ->
                if (match.range.first > cursor) append(source.substring(cursor, match.range.first))
                val groups = match.groupValues
                when {
                    groups[1].isNotEmpty() -> withStyle(Bold) { append(groups[1]) }
                    groups[2].isNotEmpty() -> withStyle(Bold) { append(groups[2]) }
                    groups[3].isNotEmpty() -> withStyle(Italic) { append(groups[3]) }
                    groups[4].isNotEmpty() -> withStyle(Italic) { append(groups[4]) }
                    groups[5].isNotEmpty() -> withStyle(Code) { append(groups[5]) }
                    groups[6].isNotEmpty() -> withStyle(linkStyle) { append(groups[6]) }
                    else -> append(match.value)
                }
                cursor = match.range.last + 1
            }
            if (cursor < source.length) append(source.substring(cursor))
        }

    /** Markup-free rendering, used for content descriptions and in tests. */
    fun plain(markdown: String): String = annotate(markdown).text

    private val Bold = SpanStyle(fontWeight = FontWeight.SemiBold)
    private val Italic = SpanStyle(fontStyle = FontStyle.Italic)
    private val Code = SpanStyle(fontFamily = FontFamily.Monospace)
}
