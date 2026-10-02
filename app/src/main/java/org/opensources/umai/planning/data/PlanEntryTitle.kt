package org.opensources.umai.planning.data

/**
 * Mealie has no quantity on a plan entry, so the servings eaten lead its
 * title, readable in Mealie too: "2 × Coffee". One serving writes nothing,
 * which keeps the entries made before, and by Mealie itself, as they are.
 * Only the multiplication sign counts, so a note such as "2 x eggs" stays a note.
 */
object PlanEntryTitle {

    private val prefix = Regex("""^(\d{1,3})\s*×\s*""")

    /** The servings and the rest of [title]; one serving when it gives no count. */
    fun parse(title: String): Pair<Int, String> {
        val match = prefix.find(title) ?: return 1 to title
        val servings = match.groupValues[1].toInt()
        if (servings < 1) return 1 to title
        return servings to title.substring(match.range.last + 1)
    }

    fun format(servings: Int, title: String): String = if (servings == 1) title else "$servings × $title".trimEnd()
}
