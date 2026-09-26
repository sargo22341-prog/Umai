package org.opensources.umai.planning.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.opensources.umai.llm.domain.AiSense
import org.opensources.umai.llm.domain.LanguageModel
import org.opensources.umai.llm.domain.LlmFailure
import org.opensources.umai.llm.domain.LlmOutcome
import org.opensources.umai.llm.domain.LlmProgress
import org.opensources.umai.llm.domain.LlmRequest

class NutritionLabelReaderTest {

    private class Answering(private val outcome: LlmOutcome) : LanguageModel {
        var asked: LlmRequest? = null
        override suspend fun isReady() = true
        override val contextSize = 4_096
        override suspend fun generate(request: LlmRequest, onProgress: (LlmProgress) -> Unit): LlmOutcome {
            asked = request
            return outcome
        }
    }

    @Test
    fun `a can is read per 100 ml, with the energy of the line below and its can`() {
        // The energy of a can is printed on two lines, kJ then kcal.
        val reading = NutritionLabelReader.parse(
            """
            {"columns": ["100 ml", "330 ml"],
             "rows": [
               {"name": "Energie :", "values": ["180 kJ /", "594 kJ /"]},
               {"name": "", "values": ["42 kcal", "139 kcal"]},
               {"name": "Matières grasses :", "values": ["0 g", "0 g"]},
               {"name": "dont acides gras saturés :", "values": ["0 g", "0 g"]},
               {"name": "Glucides :", "values": ["10.6 g", "35 g"]},
               {"name": "dont sucres :", "values": ["10.6 g", "35 g"]},
               {"name": "Protéines :", "values": ["0 g", "0 g"]},
               {"name": "Sel :", "values": ["0 g", "0 g"]}
             ]}
            """.trimIndent(),
        )

        assertNotNull(reading)
        reading!!
        assertEquals(FoodUnit.MILLILITRE, reading.unit)
        assertEquals(330.0, reading.portion)
        assertEquals(
            mapOf(
                Nutrient.ENERGY to 42.0,
                Nutrient.FAT to 0.0,
                Nutrient.SATURATED_FAT to 0.0,
                Nutrient.CARBOHYDRATES to 10.6,
                Nutrient.SUGARS to 10.6,
                Nutrient.PROTEIN to 0.0,
                Nutrient.SALT to 0.0,
            ),
            reading.per100.values,
        )
    }

    @Test
    fun `a bilingual label with one column is read per 100 g`() {
        val reading = NutritionLabelReader.parse(
            """
            {"columns": ["Pour/Per 100g"],
             "rows": [
               {"name": "Energie/Energie", "values": ["1229 kJ / 294 kcal"]},
               {"name": "Matières grasses/Vetten", "values": ["14 g"]},
               {"name": "Dont acides gras saturés/Waarvan verzadigde vetzuren", "values": ["1,4 g"]},
               {"name": "Glucides/Koolhydraten", "values": ["30 g"]},
               {"name": "Dont sucres/Waarvan suikers", "values": ["3,3 g"]},
               {"name": "Fibres alimentaires/Voedingsvezels", "values": ["2,8 g"]},
               {"name": "Protéines/Eiwitten", "values": ["9,8 g"]},
               {"name": "Sel/Zout", "values": ["1,3 g"]}
             ]}
            """.trimIndent(),
        )!!

        assertEquals(FoodUnit.GRAM, reading.unit)
        assertNull(reading.portion)
        assertEquals(
            mapOf(
                Nutrient.ENERGY to 294.0,
                Nutrient.FAT to 14.0,
                Nutrient.SATURATED_FAT to 1.4,
                Nutrient.CARBOHYDRATES to 30.0,
                Nutrient.SUGARS to 3.3,
                Nutrient.FIBER to 2.8,
                Nutrient.PROTEIN to 9.8,
                Nutrient.SALT to 1.3,
            ),
            reading.per100.values,
        )
    }

    @Test
    fun `the column for 100 g is found wherever it is, and a portion in cl is in ml`() {
        val reading = NutritionLabelReader.parse(
            """
            {"columns": ["Par verre (25 cl)", "Pour 100 ml"],
             "rows": [{"name": "Energy", "values": ["105 kcal", "42 kcal"]}]}
            """.trimIndent(),
        )!!

        assertEquals(42.0, reading.per100[Nutrient.ENERGY])
        assertEquals(FoodUnit.MILLILITRE, reading.unit)
        assertEquals(250.0, reading.portion)
    }

    @Test
    fun `an energy given in kJ only is turned into kcal`() {
        val reading = NutritionLabelReader.parse(
            """{"columns": ["100 g"], "rows": [{"name": "Energy", "values": ["418,4 kJ"]}]}""",
        )!!

        assertEquals(100.0, reading.per100[Nutrient.ENERGY]!!, 0.01)
    }

    @Test
    fun `a nutrient the label does not print is left unknown`() {
        val reading = NutritionLabelReader.parse(
            """{"columns": ["100 g"], "rows": [{"name": "Fat", "values": ["3 g"]}, {"name": "Fibre", "values": [""]}]}""",
        )!!

        assertEquals(mapOf(Nutrient.FAT to 3.0), reading.per100.values)
    }

    @Test
    fun `an answer without any known value is no reading`() {
        assertNull(NutritionLabelReader.parse("""{"columns": [], "rows": []}"""))
        assertNull(NutritionLabelReader.parse("""{"columns": ["100 g"], "rows": [{"name": "Ingrédients", "values": ["eau"]}]}"""))
        assertNull(NutritionLabelReader.parse("not json"))
    }

    @Test
    fun `the picture goes to the part of the model that sees`() = runBlocking {
        val model = Answering(LlmOutcome.Success("""{"columns": ["100 g"], "rows": [{"name": "Energie", "values": ["52 kcal"]}]}"""))

        val outcome = NutritionLabelReader(model).read(byteArrayOf(1, 2, 3))

        assertEquals(AiSense.SIGHT, model.asked?.media?.sense)
        assertEquals(52.0, (outcome as LabelOutcome.Read).reading.per100[Nutrient.ENERGY])
    }

    @Test
    fun `a model that cannot see or finds nothing says so`() = runBlocking {
        val blind = NutritionLabelReader(Answering(LlmOutcome.Failure(LlmFailure.MEDIA_UNSUPPORTED)))
        val empty = NutritionLabelReader(Answering(LlmOutcome.Success("""{"columns": [], "rows": []}""")))

        assertEquals(LabelOutcome.Failed(LlmFailure.MEDIA_UNSUPPORTED), blind.read(byteArrayOf(1)))
        assertEquals(LabelOutcome.NothingFound, empty.read(byteArrayOf(1)))
    }
}
