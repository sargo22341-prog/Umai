package org.opensources.umai.search.domain

import org.opensources.umai.core.model.Organizer
import java.text.Normalizer

/**
 * The categories or tags offered while the user types in a filter field.
 *
 * Nothing is suggested before something is typed: an instance can hold
 * hundreds of tags, and a wall of chips is harder to scan than a search field.
 * Matching ignores case and accents ("gateau" finds "Gâteau"), names that start
 * with the query come first, and entries already picked are left out since
 * they are shown on their own.
 */
fun List<Organizer>.suggestionsFor(
    query: String,
    selectedIds: Set<String>,
    limit: Int = MAX_SUGGESTIONS,
): List<Organizer> {
    val needle = query.foldForSearch()
    if (needle.isEmpty()) return emptyList()
    return asSequence()
        .filter { it.id !in selectedIds }
        .map { it to it.name.foldForSearch() }
        .filter { (_, name) -> needle in name }
        .sortedWith(compareBy({ (_, name) -> !name.startsWith(needle) }, { (_, name) -> name }))
        .map { (organizer, _) -> organizer }
        .take(limit)
        .toList()
}

private fun String.foldForSearch(): String =
    Normalizer.normalize(trim(), Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .lowercase()

private val DIACRITICS = Regex("\\p{Mn}+")

private const val MAX_SUGGESTIONS = 30
