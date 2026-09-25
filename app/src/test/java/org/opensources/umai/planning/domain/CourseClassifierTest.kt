package org.opensources.umai.planning.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.opensources.umai.core.model.MealType

class CourseVocabularyTest {

    @Test
    fun `categories are recognized by their names, whatever the instance calls them`() {
        assertEquals(DishCourse.DESSERT, CourseVocabulary.ofOrganizer("Desserts"))
        assertEquals(DishCourse.DESSERT, CourseVocabulary.ofOrganizer("Pâtisserie"))
        assertEquals(DishCourse.DESSERT, CourseVocabulary.ofOrganizer("Recettes sucrées"))
        assertEquals(DishCourse.DRINK, CourseVocabulary.ofOrganizer("Boissons chaudes"))
        assertEquals(DishCourse.DRINK, CourseVocabulary.ofOrganizer("Cocktails"))
        assertEquals(DishCourse.OTHER, CourseVocabulary.ofOrganizer("Entrées"))
        assertEquals(DishCourse.OTHER, CourseVocabulary.ofOrganizer("Accompagnements"))
        assertEquals(DishCourse.OTHER, CourseVocabulary.ofOrganizer("Petit-déjeuner"))
        assertEquals(DishCourse.MAIN, CourseVocabulary.ofOrganizer("Plats principaux"))
        assertEquals(DishCourse.MAIN, CourseVocabulary.ofOrganizer("Main dishes"))
    }

    @Test
    fun `names that say nothing about the course give no answer`() {
        assertNull(CourseVocabulary.ofOrganizer("Italien"))
        assertNull(CourseVocabulary.ofOrganizer("Rapide"))
        assertNull(CourseVocabulary.ofOrganizer("Sucré-salé"))
        assertNull(CourseVocabulary.ofOrganizer("Thermomix"))
        assertNull(CourseVocabulary.ofOrganizer("Fondue"))
    }

    @Test
    fun `a recipe name only counts by its head`() {
        assertEquals(DishCourse.DESSERT, CourseVocabulary.ofRecipeName("Tiramisu aux fraises"))
        assertEquals(DishCourse.DESSERT, CourseVocabulary.ofRecipeName("Le meilleur fondant au chocolat"))
        assertEquals(DishCourse.DRINK, CourseVocabulary.ofRecipeName("Mojito"))
        assertEquals(DishCourse.OTHER, CourseVocabulary.ofRecipeName("Sauce bolognaise"))
        assertNull(CourseVocabulary.ofRecipeName("Poulet sauce curry"))
        assertNull(CourseVocabulary.ofRecipeName("Poulet fondant au four"))
    }
}

class CourseClassifierTest {

    private val desserts = organizer("c-dessert", "Desserts")
    private val mains = organizer("c-main", "Plats")
    private val italian = organizer("t-it", "Italien")

    private fun classify(
        recipe: org.opensources.umai.core.model.RecipeSummary,
        userCourses: Map<String, DishCourse> = emptyMap(),
        pastMeals: Map<MealType, Int> = emptyMap(),
    ) = CourseClassifier.classify(recipe, { CourseVocabulary.ofOrganizer(it.name) }, userCourses, pastMeals)

    @Test
    fun `a recipe filed under desserts is a dessert`() {
        assertEquals(DishCourse.DESSERT, classify(summary("1", "Tarte", categories = listOf(desserts))))
    }

    @Test
    fun `a recipe filed under dishes is a dish`() {
        assertEquals(DishCourse.MAIN, classify(summary("1", "Lasagnes", categories = listOf(mains, italian))))
    }

    @Test
    fun `the choice of the user settles it`() {
        val recipe = summary("1", "Tarte", categories = listOf(italian))
        assertEquals(DishCourse.DESSERT, classify(recipe, userCourses = mapOf("t-it" to DishCourse.DESSERT)))
        assertEquals(
            DishCourse.MAIN,
            classify(summary("2", "Tiramisu", categories = listOf(desserts)), userCourses = mapOf("c-dessert" to DishCourse.MAIN)),
        )
    }

    @Test
    fun `past meals tell a dish from a dessert`() {
        assertEquals(DishCourse.MAIN, classify(summary("1", "Blanquette"), pastMeals = mapOf(MealType.DINNER to 2)))
        assertEquals(DishCourse.DESSERT, classify(summary("1", "Salade d'agrumes"), pastMeals = mapOf(MealType.DESSERT to 1)))
    }

    @Test
    fun `a clue of something else wins over an equal clue of a dish`() {
        assertEquals(DishCourse.DESSERT, classify(summary("1", "Cookies", categories = listOf(mains))))
    }

    @Test
    fun `a recipe with no clue is left undecided`() {
        assertNull(classify(summary("1", "Blanquette de veau", categories = listOf(italian))))
    }
}

class IngredientKeysTest {

    @Test
    fun `the same food reads the same, whatever the quantity and wording`() {
        assertEquals("creme", IngredientKeys.nameIn("200 g de crème fraîche"))
        assertEquals("creme", IngredientKeys.nameIn("20 cl de crème liquide"))
        assertEquals("ail", IngredientKeys.nameIn("2 gousses d'ail hachées"))
        assertEquals("oignon", IngredientKeys.nameIn("1 oignon"))
        assertEquals("oignon", IngredientKeys.nameIn("3 oignons émincés"))
        assertEquals("pomme terre", IngredientKeys.nameIn("500 g de pommes de terre"))
        assertEquals("huile olive", IngredientKeys.nameIn("5 cl d'huile d'olive"))
        assertEquals("pate pizza", IngredientKeys.nameIn("1 pâte à pizza"))
        assertEquals("pate brisee", IngredientKeys.nameIn("1 pâte brisée"))
        assertEquals("sugar", IngredientKeys.nameIn("2 tbsp sugar"))
    }

    @Test
    fun `a food linked by Mealie names the line`() {
        assertEquals("tomate", IngredientKeys.keyOf(line("3 belles tomates bien mûres", food = "Tomates")))
    }

    @Test
    fun `optional lines are left out`() {
        val lines = listOf(line("1 oignon"), line("Persil (facultatif)"), line("Piment, optionnel"), line("Cheddar, optional"))
        assertEquals(setOf("oignon"), IngredientKeys.keysOf(lines))
    }

    @Test
    fun `labels keep the accents of what is written`() {
        assertEquals(mapOf("creme" to "crème", "pomme terre" to "pommes de terre"),
            IngredientKeys.labelsOf(listOf(line("20 cl de crème"), line("500 g de pommes de terre"))))
    }

    @Test
    fun `sweet ingredients without savoury ones read like a dessert`() {
        assertEquals(true, IngredientKeys.looksSweet(setOf("farine", "sucre", "oeuf", "beurre")))
        assertEquals(false, IngredientKeys.looksSweet(setOf("farine", "sucre", "oignon", "poulet")))
        assertEquals(false, IngredientKeys.looksSweet(setOf("farine", "oeuf")))
    }
}
