package org.opensources.umai.youtube.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientLineTest {

    @Test
    fun `quantities are read in every usual form`() {
        assertEquals(LeadingQuantity("200", 200.0, "g"), IngredientLine.quantityOf("200 g de farine"))
        assertEquals(LeadingQuantity("200", 200.0, "ml"), IngredientLine.quantityOf("200ml d’huile d’olive"))
        assertEquals(0.5, IngredientLine.quantityOf("1/2 citron jaune")?.amount)
        assertEquals(0.5, IngredientLine.quantityOf("½ l de lait entier")?.amount)
        assertEquals(1.5, IngredientLine.quantityOf("1,5 kg de bœuf")?.amount)
        assertEquals(2.0, IngredientLine.quantityOf("Deux œufs")?.amount)
        assertEquals("Deux", IngredientLine.quantityOf("Deux œufs")?.text)
        assertNull(IngredientLine.quantityOf("Sel"))
        assertNull(IngredientLine.quantityOf("vanille liquide"))
    }

    @Test
    fun `a quantity is taken off with its unit`() {
        assertEquals("Sel", IngredientLine.withoutQuantity("100 g de sel"))
        assertEquals("Huile d'olive", IngredientLine.withoutQuantity("5 cl d'huile d'olive"))
        assertEquals("Œufs", IngredientLine.withoutQuantity("2 œufs"))
        assertEquals("Vanilla extract", IngredientLine.withoutQuantity("2 teaspoons vanilla extract"))
    }

    @Test
    fun `amounts nobody could mean are not plausible`() {
        assertFalse(IngredientLine.isPlausible(IngredientLine.quantityOf("0 g de sel")!!))
        assertFalse(IngredientLine.isPlausible(IngredientLine.quantityOf("40 l de lait")!!))
        assertFalse(IngredientLine.isPlausible(IngredientLine.quantityOf("200 œufs")!!))
        assertTrue(IngredientLine.isPlausible(IngredientLine.quantityOf("2 kg de pommes de terre")!!))
    }

    @Test
    fun `two amounts in the same unit add up`() {
        assertEquals("2 gousses d’ail", IngredientLine.sum("1 gousse d’ail", "1 gousse d’ail"))
        assertEquals("2 jaunes d'œuf", IngredientLine.sum("1 jaune d'œuf", "1 jaune d'œuf"))
        assertEquals("300ml de lait", IngredientLine.sum("200ml de lait", "100 ml de lait"))
        assertEquals("1,5 l de lait", IngredientLine.sum("1 l de lait", "0,5 l de lait"))
        assertNull(IngredientLine.sum("1 c-à-s de parmesan", "30 g de parmesan"))
    }
}

class IngredientMergeTest {

    private fun authored(vararg lines: String) = IngredientSource(lines.toList(), authored = true)
    private fun model(vararg lines: String) = IngredientSource(lines.toList(), authored = false)

    @Test
    fun `a food repeated by the model is written once`() {
        val merged = IngredientMerge.merge(
            listOf(model("100 g de sel", "100 g de poivre", "Sel", "sel", "100 g de sel", "Poivre")),
            firstIsComplete = false,
        )

        assertEquals(listOf("100 g de sel", "100 g de poivre"), merged)
    }

    @Test
    fun `the recipe page wins a quantity conflict`() {
        val merged = IngredientMerge.merge(
            listOf(
                authored("160 g de farine", "1 œuf"),
                authored("150 g de farine"),
                model("200 g de farine", "2 œufs"),
            ),
            firstIsComplete = true,
        )

        assertEquals(listOf("160 g de farine", "1 œuf"), merged)
    }

    @Test
    fun `a food the page lists without quantity takes one another source wrote`() {
        val merged = IngredientMerge.merge(listOf(authored("Sel", "2 œufs"), authored("1 pincée de sel")), firstIsComplete = true)

        assertEquals(listOf("1 pincée de sel", "2 œufs"), merged)
    }

    @Test
    fun `a complete page is not added to, a description is`() {
        val closed = IngredientMerge.merge(listOf(authored("2 œufs"), model("Sucre")), firstIsComplete = true)
        val open = IngredientMerge.merge(listOf(authored(), authored("2 œufs"), model("Sucre")), firstIsComplete = true)

        assertEquals(listOf("2 œufs"), closed)
        assertEquals(listOf("2 œufs", "Sucre"), open)
    }

    @Test
    fun `a food an author lists twice adds up, or keeps both amounts`() {
        val merged = IngredientMerge.merge(
            listOf(authored("1 gousse d’ail", "6 filets d’anchois", "1 gousse d’ail", "1 c-à-s de parmesan râpé", "30 g de parmesan")),
            firstIsComplete = true,
        )

        assertEquals(listOf("2 gousses d’ail", "6 filets d’anchois", "1 c-à-s de parmesan râpé + 30 g"), merged)
    }

    @Test
    fun `sub-headings are not ingredients`() {
        val merged = IngredientMerge.merge(listOf(authored("La salade :", "2 blancs de poulet", "La sauce :", "1 jaune d'œuf")), firstIsComplete = true)

        assertEquals(listOf("2 blancs de poulet", "1 jaune d'œuf"), merged)
    }
}

class IngredientEvidenceTest {

    private val transcript = "alors pour la sauce il me faut deux jaunes d'œufs, 200 grammes d'huile d'olive, " +
        "un peu de sel et du poivre, puis le parmesan"

    @Test
    fun `a quantity said next to its food is kept`() {
        assertEquals(
            listOf("2 jaunes d'œufs", "200 g d'huile d'olive"),
            IngredientEvidence.checked(listOf("2 jaunes d'œufs", "200 g d'huile d'olive"), transcript),
        )
    }

    @Test
    fun `an invented quantity is taken off, the food stays`() {
        assertEquals(
            listOf("Sel", "Poivre", "Parmesan"),
            IngredientEvidence.checked(listOf("100 g de sel", "100 g de poivre", "100 g de parmesan"), transcript),
        )
    }

    @Test
    fun `a number said without the unit does not make the unit`() {
        // "200" is said, in grammes: 200 ml were never said.
        assertEquals(listOf("Huile d'olive"), IngredientEvidence.checked(listOf("200 ml d'huile d'olive"), transcript))
    }

    @Test
    fun `a food never named anywhere is dropped`() {
        assertEquals(emptyList<String>(), IngredientEvidence.checked(listOf("100 g de croûtons", "1 citron"), transcript))
    }

    @Test
    fun `an implausible amount is taken off even when said`() {
        assertEquals(listOf("Sel"), IngredientEvidence.checked(listOf("0 g de sel"), "0 g de sel"))
    }

    @Test
    fun `a vague amount is kept as it is`() {
        assertEquals(listOf("quelques feuilles de basilic"), IngredientEvidence.checked(listOf("quelques feuilles de basilic"), "du basilic"))
    }
}

class RecipeIngredientsTest {

    private val caesar = video(
        description = "⚖️ Quantités de la recette : https://tinyurl.com/3xddjkdm",
        transcript = listOf(
            TranscriptCue(10.0, 14.0, "pour la sauce deux jaunes d'œufs, du parmesan, de l'ail"),
            TranscriptCue(14.0, 18.0, "des anchois, de la moutarde et de l'huile d'olive, du sel"),
        ),
    )

    private val page = RecipePage(
        url = "https://philippe-etchebest.com/salade-cesar/",
        ingredients = listOf("1 gousse d’ail", "6 filets d’anchois", "1 gousse d’ail", "1 jaune d’œuf", "200ml d’huile d’olive"),
        servings = 6,
    )

    /** What Qwen3.5 4B answered for this video before the fix: invented amounts, lines repeated. */
    private val modelAnswer = listOf(
        "200 g de poulet cuit", "100 g de parmesan râpé", "100 g de moutarde", "100 g de sel", "100 g d'ail",
        "100 g d'huile d'olive", "100 g de sel", "100 g d'ail", "100 g d'huile d'olive",
    )

    @Test
    fun `the recipe page gives the whole list, the model adds nothing to it`() {
        assertEquals(
            listOf("2 gousses d’ail", "6 filets d’anchois", "1 jaune d’œuf", "200ml d’huile d’olive"),
            RecipeIngredients.of(caesar, page, modelAnswer),
        )
    }

    @Test
    fun `without a page the model's list is checked and has no duplicate`() {
        assertEquals(
            listOf("Parmesan râpé", "Moutarde", "Sel", "Ail", "Huile d'olive"),
            RecipeIngredients.of(caesar, page = null, modelLines = modelAnswer),
        )
    }

    @Test
    fun `without page nor model the description list is used as written`() {
        val video = video(description = "Ingrédients :\n250 g de pâtes\n500 g de viande hachée\nSel")

        assertEquals(listOf("250 g de pâtes", "500 g de viande hachée", "Sel"), RecipeIngredients.of(video, page = null, modelLines = null))
    }

    @Test
    fun `the list known before the model reads the video is the page's, else the description's`() {
        assertEquals(page.ingredients, RecipeIngredients.known(caesar, page))
        assertEquals(listOf("2 œufs"), RecipeIngredients.known(video(description = "Ingrédients :\nPâte :\n2 œufs"), page = null))
    }
}
