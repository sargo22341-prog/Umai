package org.opensources.umai.planning.domain

import java.text.Normalizer

/**
 * What a recipe is in a meal. The automatic planning only puts [MAIN] dishes
 * at lunch and dinner: a dessert, a drink, a side or a sauce is never
 * planned as a meal.
 */
enum class DishCourse {
    MAIN,
    DESSERT,
    DRINK,

    /** A starter, a side, a sauce, a bread, a breakfast: food, but not a meal on its own. */
    OTHER,
}

/**
 * Recognizes courses in words, in French and English: the names of the
 * categories and tags of the instance, and the names of recipes.
 *
 * Nothing here knows the categories of any instance: they are read from
 * Mealie and their names are matched against cooking vocabulary, so a
 * category called "Desserts", "Pâtisserie" or "Sweets" is recognized
 * wherever it is. A name that says nothing about the course, such as
 * "Italian" or "Quick", gives no answer.
 */
object CourseVocabulary {

    /** The course a category or tag name designates, `null` when it designates none. */
    fun ofOrganizer(name: String): DishCourse? = match(name, organizerTerms)

    /**
     * The course a recipe name gives away, `null` for most names, which do not.
     * Only the head of the name counts, articles aside: "Tarte tatin" is a
     * dessert, "Poulet sauce curry" is not a sauce.
     */
    fun ofRecipeName(name: String): DishCourse? {
        val head = words(name).dropWhile { it in leadingWords }
        return recipeTerms.firstOrNull { (_, phrases) -> phrases.any { startsWith(head, it) } }?.first
    }

    internal fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(marks, "")

    internal fun words(text: String): List<String> = fold(text).split(separators).filter { it.isNotEmpty() }

    /**
     * The first course whose terms appear in [text], compared word by word:
     * a term matches whole words at their start, so "dessert" matches
     * "desserts" but "the" does not match "thermomix".
     */
    private fun match(text: String, terms: List<Pair<DishCourse, List<List<String>>>>): DishCourse? {
        val words = words(text)
        if (words.isEmpty()) return null
        return terms.firstOrNull { (_, phrases) -> phrases.any { phrase -> contains(words, phrase) } }?.first
    }

    private fun contains(words: List<String>, phrase: List<String>): Boolean =
        (0..words.size - phrase.size).any { start -> startsWith(words.drop(start), phrase) }

    private fun startsWith(words: List<String>, phrase: List<String>): Boolean {
        if (phrase.size > words.size) return false
        return phrase.indices.all { i ->
            val word = words[i]
            val term = phrase[i]
            // A word may take a plural or feminine ending: "desserts", "sucrées".
            word == term || (word.startsWith(term) && word.length - term.length <= 2)
        }
    }

    /** Words a recipe name may open with before the dish itself. */
    private val leadingWords = setOf(
        "le", "la", "les", "l", "un", "une", "des", "mon", "ma", "mes", "notre", "nos", "recette", "vrai",
        "vraie", "meilleur", "meilleure", "super", "petit", "petits", "petite", "petites", "mini",
        "the", "a", "an", "my", "our", "best", "easy", "quick", "simple", "homemade",
    )

    private fun terms(vararg phrases: String) = phrases.map { it.split(' ') }

    private val marks = Regex("\\p{Mn}+")
    private val separators = Regex("[^a-z0-9]+")

    /**
     * Checked in this order: a "dessert drink" is a drink, and a category
     * that names a meal course is checked after the more specific ones.
     */
    private val organizerTerms = listOf(
        DishCourse.DRINK to terms(
            "boisson", "cocktail", "mocktail", "smoothie", "jus", "limonade", "sirop", "milkshake", "infusion",
            "drink", "beverage", "juice", "lemonade", "punch",
        ),
        DishCourse.DESSERT to terms(
            "dessert", "patisserie", "gateau", "sucree", "biscuit", "cookie", "brownie", "muffin",
            "cupcake", "glace", "sorbet", "entremet", "viennoiserie", "confiserie", "friandise", "gouter",
            "chocolat", "sweet", "cake", "pastry", "pastries", "baking", "candy", "ice cream", "tarte sucree",
        ),
        DishCourse.OTHER to terms(
            "entree", "starter", "appetizer", "aperitif", "apero", "amuse bouche", "tapas", "accompagnement",
            "side", "garniture", "sauce", "condiment", "vinaigrette", "marinade", "dip", "tartinade",
            "confiture", "jam", "pain", "bread", "boulangerie", "petit dejeuner", "petit dej", "breakfast",
            "brunch", "snack", "encas", "basique", "dressing", "spread", "pickle", "conserve",
        ),
        DishCourse.MAIN to terms(
            "plat", "plat principal", "plats principaux", "plat unique", "main", "main course", "main dish",
            "diner", "dinner", "dejeuner", "lunch", "supper", "repas",
        ),
    )

    /** Dishes whose name alone tells the course. */
    private val recipeTerms = listOf(
        DishCourse.DRINK to terms(
            "cocktail", "mocktail", "smoothie", "jus de", "limonade", "sirop de", "milkshake", "chocolat chaud",
            "the glace", "cafe glace", "sangria", "mojito", "spritz", "punch", "lemonade", "juice", "hot chocolate",
        ),
        DishCourse.DESSERT to terms(
            "tiramisu", "clafoutis", "crumble", "panna cotta", "fondant", "moelleux", "brownie", "cookie",
            "muffin", "madeleine", "financier", "macaron", "cheesecake", "creme brulee", "creme caramel",
            "creme dessert", "mousse au chocolat", "ile flottante", "profiterole", "eclair", "flan patissier",
            "tarte tatin", "tarte au citron", "tarte aux pommes", "tarte aux fraises", "compote", "salade de fruits",
            "gateau", "cake au citron", "cake marbre", "brioche", "gaufre", "beignet", "churros", "pudding",
            "fraisier", "charlotte", "bavarois", "sorbet", "glace a", "meringue", "pavlova", "cupcake", "donut",
            "banana bread", "carrot cake", "riz au lait", "pain perdu", "dessert", "apple pie", "pie aux pommes",
        ),
        DishCourse.OTHER to terms(
            "sauce", "vinaigrette", "mayonnaise", "pesto", "houmous", "hummus", "guacamole", "tapenade",
            "confiture", "pate a pizza", "pate brisee", "pate feuilletee", "pate sablee", "pate a crepe",
            "baguette", "focaccia", "bouillon", "fond de", "marinade", "pickles", "granola", "porridge",
            "pancake", "dressing", "chutney", "ketchup",
        ),
    )
}
