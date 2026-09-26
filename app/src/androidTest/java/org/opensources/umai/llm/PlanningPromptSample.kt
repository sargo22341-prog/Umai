package org.opensources.umai.llm

import kotlinx.coroutines.runBlocking
import org.opensources.umai.llm.domain.LanguageModel
import org.opensources.umai.llm.domain.LlmFailure
import org.opensources.umai.llm.domain.LlmOutcome
import org.opensources.umai.llm.domain.LlmProgress
import org.opensources.umai.llm.domain.LlmRequest
import org.opensources.umai.planning.domain.ModelCourseClassifier
import org.opensources.umai.planning.domain.UnplacedRecipe

/**
 * The request the automatic planning sends for a full batch of recipes that
 * nothing on the instance places: 25 French recipes, a few of each course.
 */
internal object PlanningPromptSample {

    /** The expected course of each code, where a cook would not hesitate. */
    val expected = mapOf(
        "r1" to "main", "r2" to "dessert", "r3" to "drink", "r4" to "main", "r7" to "dessert",
        "r8" to "main", "r16" to "main", "r17" to "dessert", "r20" to "main", "r22" to "main", "r25" to "drink",
    )

    /** The request for the first [size] recipes: a full batch, or the few left over after one. */
    fun request(size: Int = recipes.size): LlmRequest {
        val asked = mutableListOf<LlmRequest>()
        val capture = object : LanguageModel {
            override suspend fun isReady() = true

            override val contextSize = 4_096

            override suspend fun generate(request: LlmRequest, onProgress: (LlmProgress) -> Unit): LlmOutcome {
                asked += request
                return LlmOutcome.Failure(LlmFailure.NOT_READY)
            }
        }
        runBlocking { ModelCourseClassifier(capture).classify(recipes.take(size)) }
        return asked.single()
    }

    val recipes = listOf(
        UnplacedRecipe("1", "Lasagnes à la bolognaise", listOf("pâtes à lasagne", "bœuf haché", "tomate", "oignon", "béchamel", "parmesan")),
        UnplacedRecipe("2", "Tarte tatin", listOf("pomme", "sucre", "beurre", "pâte feuilletée")),
        UnplacedRecipe("3", "Mojito", listOf("rhum", "menthe", "citron vert", "sucre", "eau gazeuse")),
        UnplacedRecipe("4", "Poulet basquaise", listOf("poulet", "poivron", "tomate", "oignon", "ail", "piment d'Espelette")),
        UnplacedRecipe("5", "Houmous", listOf("pois chiche", "tahini", "citron", "ail", "huile d'olive")),
        UnplacedRecipe("6", "Gratin dauphinois", listOf("pomme de terre", "crème", "lait", "ail", "muscade")),
        UnplacedRecipe("7", "Mousse au chocolat", listOf("chocolat noir", "œuf", "sucre")),
        UnplacedRecipe("8", "Saumon en papillote", listOf("saumon", "citron", "aneth", "courgette")),
        UnplacedRecipe("9", "Pain de mie", listOf("farine", "lait", "beurre", "levure", "sel")),
        UnplacedRecipe("10", "Curry de lentilles corail", listOf("lentille corail", "lait de coco", "curry", "oignon", "tomate")),
        UnplacedRecipe("11", "Crêpes", listOf("farine", "œuf", "lait", "sucre", "beurre")),
        UnplacedRecipe("12", "Quiche lorraine", listOf("pâte brisée", "lardon", "œuf", "crème")),
        UnplacedRecipe("13", "Vinaigrette moutarde", listOf("moutarde", "vinaigre", "huile")),
        UnplacedRecipe("14", "Risotto aux champignons", listOf("riz arborio", "champignon", "bouillon", "parmesan", "vin blanc")),
        UnplacedRecipe("15", "Smoothie banane fraise", listOf("banane", "fraise", "yaourt", "lait")),
        UnplacedRecipe("16", "Chili con carne", listOf("bœuf haché", "haricot rouge", "tomate", "oignon", "cumin")),
        UnplacedRecipe("17", "Cookies", listOf("farine", "beurre", "sucre", "pépites de chocolat", "œuf")),
        UnplacedRecipe("18", "Soupe de potiron", listOf("potiron", "oignon", "bouillon", "crème")),
        UnplacedRecipe("19", "Taboulé", listOf("semoule", "tomate", "concombre", "menthe", "persil", "citron")),
        UnplacedRecipe("20", "Bœuf bourguignon", listOf("bœuf", "vin rouge", "carotte", "oignon", "lardon", "champignon")),
        UnplacedRecipe("21", "Riz au lait", listOf("riz rond", "lait", "sucre", "vanille")),
        UnplacedRecipe("22", "Pizza margherita", listOf("pâte à pizza", "tomate", "mozzarella", "basilic")),
        UnplacedRecipe("23", "Guacamole", listOf("avocat", "citron vert", "oignon", "coriandre")),
        UnplacedRecipe("24", "Pad thaï", listOf("nouilles de riz", "crevette", "œuf", "cacahuète", "sauce poisson")),
        UnplacedRecipe("25", "Limonade maison", listOf("citron", "sucre", "eau")),
    )
}
