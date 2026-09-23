package org.opensources.umai.profile.data

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.model.HouseholdPreferences
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.NetworkError
import java.time.DayOfWeek

class ProfileRepositoryTest {

    /** Stands in for the system photo picker, which needs no server. */
    private class FakeAvatarImages(private val image: AvatarImage?) : AvatarImageReader {
        override suspend fun read(uri: String): AvatarImage? = image
    }

    private lateinit var fake: FakeMealieServer

    @Before
    fun setUp() {
        fake = FakeMealieServer()
    }

    @After
    fun tearDown() = fake.shutdown()

    private fun repository(image: AvatarImage? = null) =
        ProfileRepository({ fake.api() }, FakeAvatarImages(image))

    @Test
    fun `the signed-in account is read with its rights`() = runTest {
        fake.enqueueJson(USER)

        val user = (repository().currentUser() as ApiResult.Success).value

        assertEquals("Hiroo", user.displayName)
        assertEquals("hiroo@example.org", user.email)
        assertEquals("Maison", user.householdName)
        assertEquals("H", user.initials)
        assertTrue(user.canManageHousehold)
        assertEquals("/api/users/self", fake.takeRequest().url.encodedPath)
    }

    @Test
    fun `a two-word name yields two initials`() = runTest {
        fake.enqueueJson(USER.replace(""""Hiroo"""", """"Hiroo Sensei""""))

        assertEquals("HS", (repository().currentUser() as ApiResult.Success).value.initials)
    }

    @Test
    fun `an account with no name at all falls back to its username`() = runTest {
        fake.enqueueJson(
            """
            {"id":"u1","username":"hiroo","email":"h@example.org","group":"G","household":"M",
             "groupId":"g","groupSlug":"g","householdId":"h","householdSlug":"h","cacheKey":"k"}
            """.trimIndent(),
        )

        assertEquals("hiroo", (repository().currentUser() as ApiResult.Success).value.displayName)
    }

    @Test
    fun `an administrator may always manage the household`() = runTest {
        fake.enqueueJson(
            """
            {"id":"u1","username":"root","email":"r@example.org","admin":true,
             "canManageHousehold":false,"group":"G","household":"M",
             "groupId":"g","groupSlug":"g","householdId":"h","householdSlug":"h","cacheKey":"k"}
            """.trimIndent(),
        )

        assertTrue((repository().currentUser() as ApiResult.Success).value.canManageHousehold)
    }

    @Test
    fun `the household statistics are read`() = runTest {
        fake.enqueueJson(
            """{"totalRecipes":42,"totalUsers":2,"totalCategories":7,"totalTags":19,"totalTools":3}""",
        )

        val statistics = (repository().statistics() as ApiResult.Success).value

        assertEquals(42, statistics.recipes)
        assertEquals(19, statistics.tags)
        assertEquals("/api/households/statistics", fake.takeRequest().url.encodedPath)
    }

    @Test
    fun `the first day of the week keeps Mealie's own numbering`() = runTest {
        fake.enqueueJson(PREFERENCES)

        val preferences = (repository().householdPreferences() as ApiResult.Success).value

        assertEquals(1, preferences.firstDayOfWeek)
        assertEquals(DayOfWeek.MONDAY, preferences.firstDay)
    }

    @Test
    fun `saving the preferences sends every field back`() = runTest {
        val repository = repository()
        fake.enqueueJson(PREFERENCES)
        val current = (repository.householdPreferences() as ApiResult.Success).value
        fake.takeRequest()

        fake.enqueueJson(PREFERENCES)
        repository.updateHouseholdPreferences(
            current.copy(firstDayOfWeek = HouseholdPreferences.mealieDayNumber(DayOfWeek.SUNDAY)),
        )

        val request = fake.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/households/preferences", request.url.encodedPath)

        // The endpoint replaces the whole object: a field left out would be
        // reset to its server-side default.
        val body = request.body?.utf8().orEmpty()
        assertTrue(body.contains(""""firstDayOfWeek":0"""))
        assertTrue(body.contains(""""recipeShowNutrition":true"""))
        assertTrue(body.contains(""""privateHousehold":false"""))
        assertTrue(body.contains(""""lockRecipeEditsFromOtherHouseholds":true"""))
        assertFalse(body.contains("\"id\""))
    }

    @Test
    fun `a new avatar is uploaded as a multipart profile field`() = runTest {
        val repository = repository(AvatarImage("jpeg".toByteArray(), "image/jpeg", "profile.jpg"))
        fake.enqueueJson("{}")
        fake.enqueueJson(USER)

        val refreshed = repository.updateAvatar("u1", "content://picker/1")

        val upload = fake.takeRequest()
        assertEquals("POST", upload.method)
        assertEquals("/api/users/u1/image", upload.url.encodedPath)
        val body = upload.body?.utf8().orEmpty()
        assertTrue(body.contains("""name="profile""""))
        assertTrue(body.contains("""filename="profile.jpg""""))

        // The account is read back so the new cache key reaches the session.
        assertEquals("/api/users/self", fake.takeRequest().url.encodedPath)
        assertEquals("abc", (refreshed as ApiResult.Success).value.cacheKey)
    }

    @Test
    fun `a picture that cannot be read never reaches the server`() = runTest {
        val result = repository(image = null).updateAvatar("u1", "content://picker/1")

        assertEquals(NetworkError.InvalidResponse, (result as ApiResult.Failure).error)
        assertEquals(0, fake.server.requestCount)
    }

    private companion object {
        val USER = """
            {"id":"u1","username":"hiroo","fullName":"Hiroo","email":"hiroo@example.org",
             "admin":false,"canManageHousehold":true,"group":"Famille","household":"Maison",
             "groupId":"g1","groupSlug":"famille","householdId":"h1","householdSlug":"maison",
             "cacheKey":"abc"}
        """.trimIndent()

        val PREFERENCES = """
            {"id":"p1","privateHousehold":false,"showAnnouncements":true,
             "lockRecipeEditsFromOtherHouseholds":true,"firstDayOfWeek":1,"recipePublic":true,
             "recipeShowNutrition":true,"recipeShowAssets":false,"recipeLandscapeView":false,
             "recipeDisableComments":false}
        """.trimIndent()
    }
}
