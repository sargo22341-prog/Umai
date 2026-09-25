package org.opensources.umai.youtube.domain


/** Working on the captions of a video. */
object Transcript {

    /** Sound descriptions such as "[Musique]" or "(applause)", which are not speech. */
    private val soundTag = Regex("""\[[^]]*]|\((?:music|musique|applause|applaudissements|rires|laughter)\)""", RegexOption.IGNORE_CASE)
    private val spaces = Regex("""\s+""")

    fun clean(text: String): String = text.replace(soundTag, " ").replace(spaces, " ").trim()

    /** The words spoken between [start] and [end] seconds. */
    fun textBetween(cues: List<TranscriptCue>, start: Double, end: Double): String =
        cues.filter { it.start >= start - EDGE && it.start < end }
            .joinToString(" ") { clean(it.text) }
            .replace(spaces, " ")
            .trim()

    /**
     * The transcript as short timed paragraphs, "[125s] words", one every
     * [blockSeconds]: the form the language model reads it in, in the unit
     * it answers with. It is cut at
     * [maxChars], keeping the beginning, where the ingredients are usually shown.
     */
    fun timedBlocks(cues: List<TranscriptCue>, blockSeconds: Double = BLOCK_SECONDS, maxChars: Int = Int.MAX_VALUE): String {
        val builder = StringBuilder()
        var blockStart = -1.0
        val block = StringBuilder()
        fun flush() {
            val text = clean(block.toString())
            if (text.isNotEmpty()) {
                val line = "[${blockStart.toInt()}s] $text\n"
                if (builder.length + line.length > maxChars) return
                builder.append(line)
            }
            block.clear()
        }
        for (cue in cues) {
            if (blockStart < 0) blockStart = cue.start
            if (cue.start - blockStart >= blockSeconds) {
                flush()
                if (builder.length >= maxChars) break
                blockStart = cue.start
            }
            block.append(' ').append(cue.text)
        }
        if (builder.length < maxChars) flush()
        return builder.toString().trimEnd()
    }

    /**
     * Where each step is said in the video, from the words the step and the
     * transcript share. Steps come in order, so the starts found are too: a
     * dynamic programme picks, for every step in turn, a stretch of the
     * transcript after the previous one's, the one that shares the most words.
     *
     * Returns one start per step, in seconds; `null` for all when the
     * transcript shares too few words with the steps to be trusted.
     */
    fun alignSteps(steps: List<String>, cues: List<TranscriptCue>): List<Double>? {
        if (steps.isEmpty() || cues.isEmpty()) return null
        val windows = windows(cues)
        if (windows.size < steps.size) return null
        val stepWords = steps.map { Words.of(it) }
        val windowWords = windows.map { Words.of(it.second) }
        val score = Array(steps.size) { i -> DoubleArray(windows.size) { j -> Words.overlap(stepWords[i], windowWords[j]) } }

        // best[i][j]: best total for steps 0..i with step i at window j.
        val best = Array(steps.size) { DoubleArray(windows.size) { Double.NEGATIVE_INFINITY } }
        val from = Array(steps.size) { IntArray(windows.size) { -1 } }
        for (j in windows.indices) best[0][j] = score[0][j]
        for (i in 1 until steps.size) {
            var runningBest = Double.NEGATIVE_INFINITY
            var runningIndex = -1
            for (j in windows.indices) {
                if (j > 0 && best[i - 1][j - 1] > runningBest) {
                    runningBest = best[i - 1][j - 1]
                    runningIndex = j - 1
                }
                if (runningIndex >= 0) {
                    best[i][j] = runningBest + score[i][j]
                    from[i][j] = runningIndex
                }
            }
        }
        val last = steps.size - 1
        var j = best[last].indices.maxByOrNull { best[last][it] } ?: return null
        val total = best[last][j]
        if (total < steps.size * MIN_MEAN_OVERLAP) return null
        val picked = IntArray(steps.size)
        for (i in last downTo 0) {
            picked[i] = j
            if (i > 0) j = from[i][j]
        }
        return picked.map { windows[it].first }
    }

    /** Overlapping stretches of about [WINDOW_SECONDS], every [WINDOW_STEP] seconds. */
    private fun windows(cues: List<TranscriptCue>): List<Pair<Double, String>> {
        val end = cues.last().end
        val windows = mutableListOf<Pair<Double, String>>()
        var start = cues.first().start
        while (start < end) {
            val text = textBetween(cues, start, start + WINDOW_SECONDS)
            if (text.isNotBlank()) windows += cues.first { it.start >= start - EDGE }.start to text
            start += WINDOW_STEP
        }
        return windows.distinctBy { it.first }
    }

    private const val EDGE = 0.25
    private const val BLOCK_SECONDS = 15.0
    private const val WINDOW_SECONDS = 30.0
    private const val WINDOW_STEP = 10.0
    private const val MIN_MEAN_OVERLAP = 0.08
}

/** Content words of a text, for comparing a step with what is said. */
internal object Words {

    private val separators = Regex("""[^a-z0-9]+""")

    private val stopWords = setOf(
        // French
        "le", "la", "les", "un", "une", "des", "de", "du", "d", "l", "et", "ou", "a", "au", "aux", "en", "dans",
        "sur", "pour", "par", "avec", "sans", "puis", "on", "je", "tu", "il", "elle", "nous", "vous", "ils",
        "ce", "ca", "cette", "ces", "que", "qui", "est", "sont", "va", "vais", "faire", "fait", "bien", "tout",
        "tres", "plus", "pas", "ne", "se", "sa", "son", "ses", "votre", "vos", "mon", "ma", "mes", "y", "c",
        "j", "n", "s", "qu", "alors", "donc", "voila", "maintenant", "ensuite", "petit", "peu",
        // English
        "the", "an", "and", "or", "of", "to", "in", "on", "for", "with", "without", "then", "it", "is", "are",
        "we", "you", "i", "this", "that", "these", "now", "so", "just", "some", "your", "our", "into", "at",
        "be", "will", "going", "gonna", "get", "put", "little", "bit", "okay", "ok", "really",
    )

    /** Every word, lower case and without accents. */
    fun tokens(text: String): List<String> =
        VideoDescription.fold(text).split(separators).filter { it.isNotEmpty() }

    fun of(text: String): Set<String> =
        tokens(text)
            .filter { it.length > 2 && it !in stopWords }
            .map { it.removeSuffix("s").removeSuffix("x") }
            .toSet()

    /** Share of the step's words that appear in the window. */
    fun overlap(step: Set<String>, window: Set<String>): Double =
        if (step.isEmpty()) 0.0 else step.count { it in window }.toDouble() / step.size
}
