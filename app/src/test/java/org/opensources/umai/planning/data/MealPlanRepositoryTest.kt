package org.opensources.umai.planning.data

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.model.MealPlanEntry
import org.opensources.umai.core.model.MealType
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.query
import java.time.DayOfWeek
import java.time.LocalDate

class MealPlanRepositoryTest {

    private lateinit var fake: FakeMealieServer
    private lateinit var repository: MealPlanRepository

    @Before
    fun setUp() {
        fake = FakeMealieServer()
        repository = MealPlanRepository { fake.api() }
    }

    @After
    fun tearDown() = fake.shutdown()

    @Test
    fun `the window is sent as the documented date range`() = runTest {
        fake.enqueueJson(EMPTY_PAGE)

        repository.entries(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 28))

        val request = fake.takeRequest()
        assertEquals("/api/households/mealplans", request.url.encodedPath)
        assertEquals("2026-09-21", request.query("start_date"))
        assertEquals("2026-09-28", request.query("end_date"))
    }

    @Test
    fun `an empty plan is a success with no entry`() = runTest {
        fake.enqueueJson(EMPTY_PAGE)
        val result = repository.entries(LocalDate.now(), LocalDate.now())
        assertTrue((result as ApiResult.Success).value.isEmpty())
    }

    @Test
    fun `entries keep their recipe, day and meal slot`() = runTest {
        fake.enqueueJson(PAGE)

        val entries = (repository.entries(LocalDate.now(), LocalDate.now()) as ApiResult.Success).value

        assertEquals(2, entries.size)
        val dinner = entries[0]
        assertEquals(LocalDate.of(2026, 9, 22), dinner.date)
        assertEquals(MealType.DINNER, dinner.type)
        assertEquals("Poulet au curry", dinner.recipe?.name)
        assertEquals("Poulet au curry", dinner.displayTitle)
    }

    @Test
    fun `a free text entry without a recipe falls back to its title`() = runTest {
        fake.enqueueJson(PAGE)
        val entries = (repository.entries(LocalDate.now(), LocalDate.now()) as ApiResult.Success).value
        val note = entries[1]
        assertNull(note.recipe)
        assertEquals("Restes", note.displayTitle)
        assertEquals(MealType.LUNCH, note.type)
    }

    @Test
    fun `an entry with an unreadable date is dropped`() = runTest {
        fake.enqueueJson(
            """{"page":1,"per_page":50,"total":1,"total_pages":1,
                "items":[{"id":1,"date":"pas-une-date","entryType":"dinner"}]}""",
        )
        val entries = (repository.entries(LocalDate.now(), LocalDate.now()) as ApiResult.Success).value
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `an unknown meal slot falls back to dinner instead of failing`() = runTest {
        fake.enqueueJson(
            """{"page":1,"per_page":50,"total":1,"total_pages":1,
                "items":[{"id":1,"date":"2026-09-22","entryType":"brunch"}]}""",
        )
        val entries = (repository.entries(LocalDate.now(), LocalDate.now()) as ApiResult.Success).value
        assertEquals(MealType.DINNER, entries.single().type)
    }

    @Test
    fun `adding a recipe posts the slot and the day`() = runTest {
        fake.enqueueJson(
            """{"id":9,"date":"2026-09-23","entryType":"lunch","title":"","text":"",
                "recipeId":"r1","groupId":"g","userId":"u","householdId":"h"}""",
            code = 201,
        )

        val result = repository.add(LocalDate.of(2026, 9, 23), MealType.LUNCH, recipeId = "r1")

        assertTrue(result is ApiResult.Success)
        val request = fake.takeRequest()
        assertEquals("POST", request.method)
        val body = request.body?.utf8().orEmpty()
        assertTrue(body.contains(""""date":"2026-09-23""""))
        assertTrue(body.contains(""""entryType":"lunch""""))
        assertTrue(body.contains(""""recipeId":"r1""""))
    }

    @Test
    fun `adding a note posts a title and no recipe`() = runTest {
        fake.enqueueJson(
            """{"id":10,"date":"2026-09-23","entryType":"dinner","title":"Restaurant","text":"",
                "groupId":"g","userId":"u"}""",
            code = 201,
        )

        repository.add(LocalDate.of(2026, 9, 23), MealType.DINNER, recipeId = null, title = "Restaurant")

        val body = fake.takeRequest().body?.utf8().orEmpty()
        assertTrue(body.contains(""""title":"Restaurant""""))
    }

    @Test
    fun `deleting an entry calls the documented endpoint`() = runTest {
        fake.enqueueJson("{}")
        val result = repository.delete(9)
        assertTrue(result is ApiResult.Success)
        val request = fake.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/households/mealplans/9", request.url.encodedPath)
    }

    @Test
    fun `moving an entry needs the ownership fields Mealie requires`() = runTest {
        val orphan = MealPlanEntry(
            id = 1,
            date = LocalDate.of(2026, 9, 22),
            type = MealType.DINNER,
            title = "",
            text = "",
            recipe = null,
            groupId = null,
            userId = null,
        )
        val result = repository.update(orphan)
        assertEquals(NetworkError.InvalidResponse, (result as ApiResult.Failure).error)
    }

    @Test
    fun `the first day of the week is read from the household preferences`() = runTest {
        // Mealie numbers the days from Sunday: 0 is Sunday, not an out-of-range Monday.
        fake.enqueueJson(PREFERENCES_FROM_SUNDAY)

        val result = repository.firstDayOfWeek()

        assertEquals(DayOfWeek.SUNDAY, (result as ApiResult.Success).value)
        assertEquals("/api/households/preferences", fake.takeRequest().url.encodedPath)
    }

    @Test
    fun `an unreadable first day of the week is a failure, not a guess`() = runTest {
        fake.enqueueError(403)

        val result = repository.firstDayOfWeek()

        assertTrue(result is ApiResult.Failure)
    }

    @Test
    fun `without an instance the plan cannot be read`() = runTest {
        val offline = MealPlanRepository { null }
        val result = offline.entries(LocalDate.now(), LocalDate.now())
        assertEquals(NetworkError.Unauthorized, (result as ApiResult.Failure).error)
    }

    private companion object {
        const val PREFERENCES_FROM_SUNDAY = """
            {"privateHousehold":false,"showAnnouncements":true,
             "lockRecipeEditsFromOtherHouseholds":true,"firstDayOfWeek":0,"recipePublic":true,
             "recipeShowNutrition":true,"recipeShowAssets":false,"recipeLandscapeView":false,
             "recipeDisableComments":false}
        """

        const val EMPTY_PAGE =
            """{"page":1,"per_page":50,"total":0,"total_pages":0,"items":[],"next":null,"previous":null}"""

        val PAGE = """
        {
          "page":1,"per_page":50,"total":2,"total_pages":1,
          "items":[
            {"id":1,"date":"2026-09-22","entryType":"dinner","title":"","text":"",
             "recipeId":"r1","groupId":"g","userId":"u","householdId":"h",
             "recipe":{"id":"r1","name":"Poulet au curry","slug":"poulet-au-curry","image":"73"}},
            {"id":2,"date":"2026-09-22","entryType":"lunch","title":"Restes","text":"",
             "recipeId":null,"groupId":"g","userId":"u","householdId":"h","recipe":null}
          ]
        }
        """.trimIndent()
    }
}
