package org.opensources.umai.youtube.domain

/**
 * Rebuilds a recipe from a video with plain rules, when no language model is
 * available or it failed:
 *
 * - the ingredients are the list written in the description;
 * - the steps are the ones written in the description, placed in the video
 *   by the chapters when there is one per step, or else by the words they
 *   share with the transcript;
 * - without written steps, each chapter becomes a step, told by what is said
 *   during it; without chapters either, the transcript is cut into steps of
 *   about a minute.
 *
 * The result is a good start rather than a finished recipe: the transcript of
 * a video is speech, not instructions, and only a language model rewrites it.
 */
object RuleRecipeBuilder {

    fun build(video: YouTubeVideo): RecipeBlueprint {
        val description = video.description
        val ingredients = VideoDescription.ingredients(description).filterNot { it.endsWith(":") }
        val chapters = video.chapters
        val ingredientsChapter = chapters.firstOrNull { isIngredientsChapter(it.title) }
        val cookingChapters = chapters.withIndex().filter { (_, chapter) ->
            !isIngredientsChapter(chapter.title) && !isAside(chapter.title)
        }

        val written = VideoDescription.steps(description)
        val steps = when {
            written.isNotEmpty() -> {
                val starts = if (cookingChapters.size == written.size) {
                    cookingChapters.map { it.value.start }
                } else {
                    Transcript.alignSteps(written, video.transcript)
                }
                written.mapIndexed { index, text -> BlueprintStep(title = "", text = text, start = starts?.getOrNull(index)) }
            }
            cookingChapters.isNotEmpty() -> cookingChapters.map { (index, chapter) ->
                val spoken = Transcript.textBetween(video.transcript, chapter.start, video.chapterEnd(index))
                BlueprintStep(
                    title = chapter.title,
                    text = excerpt(spoken).ifBlank { chapter.title },
                    start = chapter.start,
                )
            }
            video.transcript.isNotEmpty() -> transcriptSteps(video)
            else -> emptyList()
        }

        return RecipeBlueprint(
            name = cleanTitle(video.title),
            summary = summary(description),
            servings = VideoDescription.servings(description) ?: VideoDescription.servings(video.title),
            prepMinutes = null,
            cookMinutes = null,
            ingredients = ingredients,
            steps = steps.withOrderedStarts(video.durationSeconds),
            ingredientsStart = ingredientsChapter?.start,
            origin = BlueprintOrigin.RULES,
            video = video,
        )
    }

    /** "Lasagnes express | Recette facile #Shorts" becomes "Lasagnes express". */
    fun cleanTitle(title: String): String {
        val withoutTags = title.replace(hashtag, " ").replace(symbols, " ").replace(spaces, " ").trim()
        val head = withoutTags.split(" | ", " - ", " – ", " — ").first().trim()
        return head.takeIf { it.length >= MIN_TITLE_LENGTH } ?: withoutTags
    }

    /** The first paragraph of the description, when it is prose rather than a list or links. */
    fun summary(description: String): String {
        val paragraph = description.split(Regex("""\n\s*\n""")).firstOrNull().orEmpty().trim()
        val usable = paragraph.isNotEmpty() && !paragraph.contains("http") &&
            paragraph.lines().size <= MAX_SUMMARY_LINES
        return if (usable) paragraph.take(MAX_SUMMARY_LENGTH).trim() else ""
    }

    internal fun isIngredientsChapter(title: String): Boolean {
        val text = VideoDescription.fold(title)
        return text.startsWith("ingredient") || text.startsWith("les ingredient") || text == "liste des ingredients"
    }

    /** Chapters that show no cooking: the greeting, the tasting, the goodbye. */
    internal fun isAside(title: String): Boolean {
        val words = Words.tokens(title)
        return asides.any { aside -> words.take(aside.size) == aside }
    }

    /** Steps of about a minute, cut where the cook pauses. */
    private fun transcriptSteps(video: YouTubeVideo): List<BlueprintStep> {
        val steps = mutableListOf<BlueprintStep>()
        var start = video.transcript.first().start
        val builder = StringBuilder()
        video.transcript.forEachIndexed { index, cue ->
            builder.append(' ').append(cue.text)
            val next = video.transcript.getOrNull(index + 1)
            val long = (next?.start ?: Double.MAX_VALUE) - start >= STEP_SECONDS
            if (next == null || long) {
                val text = excerpt(builder.toString())
                if (text.isNotBlank()) steps += BlueprintStep(title = "", text = text, start = start)
                builder.clear()
                next?.let { start = it.start }
            }
        }
        return steps
    }

    /** What is said, cleaned and cut at a sentence end short of [MAX_STEP_LENGTH]. */
    private fun excerpt(spoken: String): String {
        val text = Transcript.clean(spoken)
        if (text.length <= MAX_STEP_LENGTH) return text.replaceFirstChar { it.uppercase() }
        val cut = text.take(MAX_STEP_LENGTH)
        val end = cut.lastIndexOfAny(charArrayOf('.', '!', '?'))
        val sentence = if (end > MAX_STEP_LENGTH / 2) cut.take(end + 1) else "${cut.substringBeforeLast(' ')}…"
        return sentence.replaceFirstChar { it.uppercase() }
    }

    private val hashtag = Regex("""#\S+""")
    private val symbols = Regex("""[\p{So}\p{Cn}]""")
    private val spaces = Regex("""\s+""")
    /** The first words of such chapters, compared as whole words: "fin" is not "finitions". */
    private val asides = listOf(
        "intro", "introduction", "presentation", "generique", "bonjour", "salut", "hello", "welcome",
        "bienvenue", "degustation", "tasting", "taste test", "conclusion", "outro", "fin", "the end", "merci",
        "abonnez", "abonne", "subscribe", "sponsor", "partenaire", "pub", "bonus", "bloopers", "resultat final",
    ).map { it.split(' ') }

    private const val MIN_TITLE_LENGTH = 4
    private const val MAX_SUMMARY_LINES = 4
    private const val MAX_SUMMARY_LENGTH = 300
    private const val MAX_STEP_LENGTH = 600
    private const val STEP_SECONDS = 60.0
}
