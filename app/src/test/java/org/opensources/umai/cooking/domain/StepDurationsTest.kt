package org.opensources.umai.cooking.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class StepDurationsTest {

    private fun minutes(value: Long) = Duration.ofMinutes(value)

    @Test
    fun `minutes are read in French and in English`() {
        assertEquals(listOf(minutes(15)), StepDurations.find("Cuire 15 min à feu doux."))
        assertEquals(listOf(minutes(20)), StepDurations.find("Laisser reposer 20 minutes."))
        assertEquals(listOf(minutes(5)), StepDurations.find("Bake for 5 mins."))
        assertEquals(listOf(minutes(10)), StepDurations.find("Enfourner 10 mn."))
    }

    @Test
    fun `hours are read alone or with their minutes`() {
        assertEquals(listOf(Duration.ofHours(2)), StepDurations.find("Laisser mijoter 2 heures."))
        assertEquals(listOf(minutes(90)), StepDurations.find("Cuire 1h30."))
        assertEquals(listOf(minutes(90)), StepDurations.find("Cuire 1 h 30 min au four."))
        assertEquals(listOf(minutes(75)), StepDurations.find("Simmer for 1 hour and 15 minutes."))
        assertEquals(listOf(minutes(90)), StepDurations.find("Compter 1,5 heure."))
    }

    @Test
    fun `seconds are read too`() {
        assertEquals(listOf(Duration.ofSeconds(30)), StepDurations.find("Mixer 30 secondes."))
        assertEquals(listOf(Duration.ofSeconds(45)), StepDurations.find("Blend for 45 sec."))
    }

    @Test
    fun `a range gives its first mark`() {
        assertEquals(listOf(minutes(10)), StepDurations.find("Cuire 10 à 12 minutes."))
        assertEquals(listOf(minutes(8)), StepDurations.find("Bake 8-10 min."))
    }

    @Test
    fun `every duration of a step is offered once, in reading order`() {
        val text = "Cuire les pâtes 10 min. Pendant ce temps, faire revenir 5 min, puis cuire encore 10 min."

        assertEquals(listOf(minutes(10), minutes(5)), StepDurations.find(text))
    }

    @Test
    fun `temperatures, quantities and words are not durations`() {
        assertTrue(StepDurations.find("Préchauffer le four à 180°C, th. 6.").isEmpty())
        assertTrue(StepDurations.find("Ajouter 200 g de farine et 2 œufs.").isEmpty())
        assertTrue(StepDurations.find("Ajouter 2 hachis et 3 minis poivrons.").isEmpty())
        assertTrue(StepDurations.find("").isEmpty())
    }

    @Test
    fun `a duration glued to a word is not read`() {
        assertTrue(StepDurations.find("Référence ABC15min").isEmpty())
    }

    @Test
    fun `absurd durations are ignored`() {
        assertTrue(StepDurations.find("Laisser lever 0 min.").isEmpty())
        assertTrue(StepDurations.find("Faire mariner 72 heures.").isEmpty())
    }
}
