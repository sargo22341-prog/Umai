package org.opensources.umai.planning.domain

import org.opensources.umai.core.model.RecipeIngredient

/**
 * Names the ingredients of a recipe so that two recipes using the same food
 * are seen as sharing it, whatever the quantity or the wording: "200 g de
 * crème fraîche" and "20 cl de crème épaisse" both need cream.
 *
 * A food Mealie linked the line to is the best name; a line without one is
 * read as text, quantities and units set aside. Either way the name is cut
 * to its head, the food itself without what describes it ("ail haché" is
 * "ail"), keeping a complement ("pomme de terre", "huile d'olive").
 */
object IngredientKeys {

    /** The name of what [ingredient] needs to buy, `null` for an optional line or a heading. */
    fun keyOf(ingredient: RecipeIngredient): String? = named(ingredient)?.key

    /** The distinct things a recipe needs to buy, optional lines left out. */
    fun keysOf(ingredients: List<RecipeIngredient>): Set<String> = ingredients.mapNotNull(::keyOf).toSet()

    /** How each thing a recipe needs to buy reads, by its key: "crème", "pomme de terre". */
    fun labelsOf(ingredients: List<RecipeIngredient>): Map<String, String> =
        ingredients.mapNotNull(::named).associate { it.key to it.label }

    private fun named(ingredient: RecipeIngredient): FoodName? {
        if (isOptional(ingredient)) return null
        val text = ingredient.food?.name?.takeIf { it.isNotBlank() }
            ?: listOf(ingredient.note, ingredient.originalText, ingredient.display).firstOrNull { !it.isNullOrBlank() }
            ?: return null
        return foodIn(text)
    }

    /**
     * Whether the line says it can be left out. Optional ingredients do not
     * weigh in the choice of recipes: they are often skipped, and would make
     * two recipes look further apart than they are.
     */
    fun isOptional(ingredient: RecipeIngredient): Boolean {
        val text = CourseVocabulary.fold(
            listOfNotNull(ingredient.note, ingredient.originalText, ingredient.display).joinToString(" "),
        )
        return optionalMarks.any { it.containsMatchIn(text) }
    }

    /** The key a food is compared by, and how it reads. */
    private data class FoodName(val key: String, val label: String)

    /** The food named in a line of text: "2 gousses d'ail hachées" gives "ail". */
    fun nameIn(text: String): String? = foodIn(text)?.key

    private fun foodIn(text: String): FoodName? {
        // The words as written, each with its folded form the decisions are made on.
        val words = text.substringBefore(',').replace(parentheses, " ").replace(fractions, " ").replace(spoons, " ")
            .lowercase()
            .split(wordSeparators)
            .filter { it.isNotEmpty() }
            .map { it to CourseVocabulary.fold(it) }
            .dropWhile { (_, folded) -> folded.any(Char::isDigit) || folded in units || folded in linkWords }
        val head = words.firstOrNull() ?: return null
        // "pomme de terre", "huile d'olive": the complement is part of the food,
        // and so is the qualifier of a head that names no food by itself:
        // "pâte brisée" and "pâtes" are not bought together.
        val link = words.getOrNull(1)
        val complement = when {
            head.second in vagueHeads -> link?.takeIf { it.second !in linkWords } ?: words.getOrNull(2)?.takeIf { link?.second in complementLinks }
            else -> words.getOrNull(2)?.takeIf { link?.second in complementLinks && it.second !in linkWords }
        }
        val key = listOfNotNull(head, complement).joinToString(" ") { singular(it.second) }
        val label = when {
            complement == null || link == null -> head.first
            complement == link -> "${head.first} ${complement.first}"
            link.second == "d" -> "${head.first} d'${complement.first}"
            else -> "${head.first} ${link.first} ${complement.first}"
        }
        return FoodName(key, label)
    }

    /**
     * Whether the ingredients of a recipe read like a sweet dish: sugar,
     * chocolate or the like, and nothing savoury. Used only for recipes no
     * category, tag or past meal places.
     */
    fun looksSweet(keys: Set<String>): Boolean {
        val sweet = keys.count { key -> sweetFoods.any { key.startsWith(it) } }
        val savory = keys.count { key -> savoryFoods.any { key.startsWith(it) } }
        return sweet >= 1 && savory == 0
    }

    private fun singular(word: String): String = when {
        word.length > 3 && (word.endsWith("s") || word.endsWith("x")) -> word.dropLast(1)
        else -> word
    }

    private val parentheses = Regex("""\([^)]*\)""")
    private val wordSeparators = Regex("""[^\p{L}0-9]+""")
    private val fractions = Regex("""[½¼¾⅓⅔⅛]""")

    /** "c-à-s", "c. à c.": spoons written short, whose letters would otherwise be read as a food. */
    private val spoons = Regex("""\bc\.?\s*-?\s*[àa]\s*-?\s*[sc]\b\.?""", RegexOption.IGNORE_CASE)

    private val optionalMarks = listOf(
        Regex("""\bfacultati"""),
        Regex("""\boptionn?el"""),
        Regex("""\boptional\b"""),
        Regex("""\bsi (?:vous le )?(?:desire|souhaite|voulez)"""),
        Regex("""\bif (?:desired|you like|using)\b"""),
        Regex("""\bpour (?:la )?decoration\b"""),
        Regex("""\bfor garnish\b"""),
    )

    private val units = setOf(
        "g", "gr", "gramme", "grammes", "kg", "mg", "l", "litre", "litres", "ml", "cl", "dl", "c", "cs", "cc",
        "cas", "cac", "cuillere", "cuilleres", "cuil", "soupe", "cafe", "tasse", "tasses", "verre", "verres",
        "bol", "pincee", "pincees", "sachet", "sachets", "boite", "boites", "brin", "brins", "branche", "branches",
        "gousse", "gousses", "tranche", "tranches", "feuille", "feuilles", "botte", "bottes", "bouquet", "poignee",
        "poignees", "morceau", "morceaux", "pot", "pots", "barquette", "filet", "noix", "noisette", "trait", "zeste",
        "cup", "cups", "tbsp", "tsp", "tablespoon", "tablespoons", "teaspoon", "teaspoons", "oz", "ounce",
        "ounces", "lb", "lbs", "pound", "pounds", "pinch", "clove", "cloves", "slice", "slices", "can", "cans",
        "bunch", "handful", "piece", "pieces", "sprig", "sprigs", "stick", "sticks", "package",
        "un", "une", "deux", "trois", "quatre", "cinq", "six", "huit", "dix", "douzaine", "demi", "quelques",
        "one", "two", "three", "four", "five", "half", "few", "some", "a", "an", "grosse", "gros", "belle", "beau",
    )

    private val complementLinks = setOf("de", "d", "a", "of")

    /** Heads that need the next word to name a food: "pâte feuilletée", "sauce soja", "jus de citron". */
    private val vagueHeads = setOf("pate", "sauce", "jus", "poudre", "fond", "paste", "juice")

    private val linkWords = setOf("de", "d", "du", "des", "la", "le", "les", "l", "of", "the", "a", "au", "aux", "en", "pour", "for", "ou", "or", "et", "and")

    private val sweetFoods = listOf(
        "sucre", "sugar", "chocolat", "chocolate", "cacao", "cocoa", "vanille", "vanilla", "miel", "honey",
        "confiture", "caramel", "sirop erable", "maple", "levure", "baking", "mascarpone", "nutella", "praline",
        "poudre amande", "pepite",
    )

    private val savoryFoods = listOf(
        "poivre", "pepper", "ail", "garlic", "oignon", "onion", "echalote", "shallot", "bouillon", "stock",
        "poulet", "chicken", "boeuf", "beef", "porc", "pork", "veau", "agneau", "lamb", "canard", "dinde",
        "lardon", "bacon", "jambon", "ham", "saucisse", "sausage", "chorizo", "viande", "meat", "steak",
        "poisson", "fish", "saumon", "salmon", "thon", "tuna", "cabillaud", "crevette", "shrimp", "moule",
        "tomate", "tomato", "courgette", "poireau", "carotte", "carrot", "pomme terre", "potato",
        "moutarde", "mustard", "curry", "cumin", "paprika", "thym", "thyme", "laurier", "persil", "coriandre",
        "basilic", "sauce", "soy", "parmesan", "gruyere", "emmental", "mozzarella", "fromage",
    )
}
