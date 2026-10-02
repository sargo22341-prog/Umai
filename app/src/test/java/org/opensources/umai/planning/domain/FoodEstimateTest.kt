package org.opensources.umai.planning.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FoodEstimateTest {

    private fun food(name: String, amount: Double?, unit: FoodUnit, per100: Map<Nutrient, Double>) =
        EstimatedFood(name, amount, unit, NutritionFacts(per100), FoodSource.TABLE)

    private val apples = food("Pomme", 300.0, FoodUnit.GRAM, mapOf(Nutrient.ENERGY to 54.0, Nutrient.SUGARS to 9.0))
    private val coffee = food("Café", 150.0, FoodUnit.MILLILITRE, mapOf(Nutrient.ENERGY to 6.0))

    @Test
    fun `a food gives the values of the amount eaten`() {
        assertEquals(162, apples.calories)
        assertEquals(27.0, apples.nutrition?.get(Nutrient.SUGARS) ?: 0.0, 1e-9)
        assertNull(apples.copy(amount = null).calories)
    }

    @Test
    fun `a single food keeps its values, even before its amount is known`() {
        val estimate = FoodEstimate(listOf(apples.copy(amount = null)), byModel = false)

        assertNull(estimate.amount)
        assertEquals(apples.per100, estimate.per100)
        assertEquals(FoodUnit.GRAM, estimate.unit)
    }

    @Test
    fun `several foods add up, and a nutrient one of them lacks is left out`() {
        val estimate = FoodEstimate(listOf(apples, coffee), byModel = false)

        assertEquals(450.0, estimate.amount)
        assertEquals(FoodUnit.GRAM, estimate.unit)
        // 162 + 9 kcal in 450 g.
        assertEquals(38.0, estimate.per100[Nutrient.ENERGY] ?: 0.0, 1e-9)
        assertNull(estimate.per100[Nutrient.SUGARS])
    }

    @Test
    fun `drinks only are counted in millilitres`() {
        val estimate = FoodEstimate(listOf(coffee, coffee), byModel = false)

        assertEquals(FoodUnit.MILLILITRE, estimate.unit)
        assertEquals(300.0, estimate.amount)
    }

    @Test
    fun `foods are only added up once the amount of each is known`() {
        val _ = assertThrows(IllegalArgumentException::class.java) {
            val _ = FoodEstimate(listOf(apples, coffee.copy(amount = null)), byModel = false)
        }
        val _ = assertThrows(IllegalArgumentException::class.java) { val _ = FoodEstimate(emptyList(), byModel = false) }
    }
}
