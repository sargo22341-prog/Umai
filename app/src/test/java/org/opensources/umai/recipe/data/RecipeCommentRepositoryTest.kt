package org.opensources.umai.recipe.data

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.NetworkError

class RecipeCommentRepositoryTest {

    private lateinit var fake: FakeMealieServer
    private lateinit var repository: RecipeCommentRepository

    @Before
    fun setUp() {
        fake = FakeMealieServer()
        repository = RecipeCommentRepository { fake.api() }
    }

    @After
    fun tearDown() = fake.shutdown()

    @Test
    fun `the comments of a recipe are read newest first`() = runTest {
        fake.enqueueJson(COMMENTS)

        val comments = (repository.comments("poulet") as ApiResult.Success).value

        assertEquals(2, comments.size)
        assertEquals("Trop bon", comments[0].text)
        assertEquals("Hiroo", comments[0].authorName)
        assertEquals("/api/recipes/poulet/comments", fake.takeRequest().url.encodedPath)
    }

    @Test
    fun `a recipe without comment yields an empty list, not an error`() = runTest {
        fake.enqueueJson("[]")
        assertTrue((repository.comments("poulet") as ApiResult.Success).value.isEmpty())
    }

    @Test
    fun `an author falls back to the username when no full name is set`() = runTest {
        fake.enqueueJson(
            """
            [{"id":"c1","recipeId":"r1","text":"Note","createdAt":"2026-01-01T10:00:00+00:00",
              "updatedAt":"2026-01-01T10:00:00+00:00","userId":"u1",
              "user":{"id":"u1","username":"hiroo","admin":false}}]
            """.trimIndent(),
        )

        assertEquals("hiroo", (repository.comments("poulet") as ApiResult.Success).value.single().authorName)
    }

    @Test
    fun `posting a comment sends the recipe id and the text`() = runTest {
        fake.enqueueJson(
            """
            {"id":"c9","recipeId":"r1","text":"Merci","createdAt":"2026-01-01T10:00:00+00:00",
             "updatedAt":"2026-01-01T10:00:00+00:00","userId":"u1",
             "user":{"id":"u1","username":"hiroo","fullName":"Hiroo","admin":false}}
            """.trimIndent(),
        )

        val created = (repository.add("r1", "  Merci  ") as ApiResult.Success).value

        assertEquals("Merci", created.text)
        val request = fake.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/comments", request.url.encodedPath)
        val body = request.body?.utf8().orEmpty()
        assertTrue(body.contains(""""recipeId":"r1""""))
        // The text is trimmed before it leaves the app.
        assertTrue(body.contains(""""text":"Merci""""))
    }

    @Test
    fun `an empty comment never reaches the server`() = runTest {
        val result = repository.add("r1", "   ")

        assertEquals(NetworkError.InvalidResponse, (result as ApiResult.Failure).error)
        assertEquals(0, fake.server.requestCount)
    }

    @Test
    fun `deleting a comment calls the comment endpoint`() = runTest {
        fake.enqueueJson("""{"message":"ok"}""")

        repository.delete("c1")

        val request = fake.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/comments/c1", request.url.encodedPath)
    }

    @Test
    fun `a refused deletion is reported as an error`() = runTest {
        fake.enqueueError(403)

        val result = repository.delete("c1")

        assertEquals(NetworkError.Unauthorized, (result as ApiResult.Failure).error)
    }

    private companion object {
        val COMMENTS = """
            [
              {"id":"c1","recipeId":"r1","text":"Trop bon",
               "createdAt":"2026-02-02T10:00:00+00:00","updatedAt":"2026-02-02T10:00:00+00:00",
               "userId":"u1","user":{"id":"u1","username":"hiroo","fullName":"Hiroo","admin":false}},
              {"id":"c2","recipeId":"r1","text":"A refaire",
               "createdAt":"2026-01-01T10:00:00+00:00","updatedAt":"2026-01-01T10:00:00+00:00",
               "userId":"u2","user":{"id":"u2","username":"ami","fullName":"Ami","admin":false}}
            ]
        """.trimIndent()
    }
}
