package org.opensources.umai.shopping.data

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.model.ShoppingItem
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.NetworkError

class ShoppingRepositoryTest {

    private lateinit var fake: FakeMealieServer
    private lateinit var repository: ShoppingRepository

    @Before
    fun setUp() {
        fake = FakeMealieServer()
        repository = ShoppingRepository { fake.api() }
    }

    @After
    fun tearDown() = fake.shutdown()

    @Test
    fun `the lists of the household are read`() = runTest {
        fake.enqueueJson(LISTS)

        val lists = (repository.lists() as ApiResult.Success).value

        assertEquals(2, lists.size)
        assertEquals("Cellier", lists[0].name)
        assertEquals("/api/households/shopping/lists", fake.takeRequest().url.encodedPath)
    }

    @Test
    fun `an instance with no list yields an empty result, not an error`() = runTest {
        fake.enqueueJson("""{"page":1,"per_page":50,"total":0,"total_pages":0,"items":[]}""")
        assertTrue((repository.lists() as ApiResult.Success).value.isEmpty())
    }

    @Test
    fun `items are sorted by position and keep their label`() = runTest {
        fake.enqueueJson(LIST_DETAIL)

        val list = (repository.list("l1") as ApiResult.Success).value

        assertEquals("Cellier", list.name)
        assertEquals(listOf("2 citrons", "Huile d'olive", "Sel"), list.items.map { it.label })
        assertEquals("Fruits", list.items[0].labelName)
        assertEquals("#e11a1f", list.items[0].labelColor)
        assertEquals(1, list.checkedCount)
    }

    @Test
    fun `an empty list is reported as empty`() = runTest {
        fake.enqueueJson("""{"id":"l1","name":"Vide","listItems":[],"recipeReferences":[]}""")
        val list = (repository.list("l1") as ApiResult.Success).value
        assertTrue(list.items.isEmpty())
        assertEquals(0, list.checkedCount)
    }

    @Test
    fun `an item without a display string falls back to its note`() = runTest {
        fake.enqueueJson(
            """{"id":"l1","name":"L","recipeReferences":[],
                "listItems":[{"id":"i1","shoppingListId":"l1","display":"","note":"Pain","position":0}]}""",
        )
        val list = (repository.list("l1") as ApiResult.Success).value
        assertEquals("Pain", list.items.single().label)
    }

    @Test
    fun `adding an item posts only the fields Umai edits`() = runTest {
        fake.enqueueJson("""{"id":"i9","shoppingListId":"l1","display":"Pain","note":"Pain"}""")

        val result = repository.addItem("l1", "Pain", quantity = 1.0, position = 3)

        assertTrue(result is ApiResult.Success)
        val request = fake.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/households/shopping/items", request.url.encodedPath)
        val body = request.body?.utf8().orEmpty()
        assertTrue(body.contains(""""shoppingListId":"l1""""))
        assertTrue(body.contains(""""note":"Pain""""))
        assertTrue(body.contains(""""position":3"""))
        // The shared food catalogue is never rewritten from the phone.
        assertFalse(body.contains(""""food":"""))
        assertFalse(body.contains(""""unit":"""))
    }

    @Test
    fun `ticking an item sends the checked flag and keeps its references`() = runTest {
        fake.enqueueJson("""{"createdItems":[],"updatedItems":[],"deletedItems":[]}""")

        val item = ShoppingItem(
            id = "i1",
            shoppingListId = "l1",
            display = "2 citrons",
            note = "citrons",
            quantity = 2.0,
            checked = true,
            position = 0,
            foodId = "f1",
            unitId = null,
            labelId = "lab1",
            labelName = "Fruits",
            labelColor = "#e11a1f",
        )
        val result = repository.updateItem(item)

        assertTrue(result is ApiResult.Success)
        val request = fake.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/households/shopping/items/i1", request.url.encodedPath)
        val body = request.body?.utf8().orEmpty()
        assertTrue(body.contains(""""checked":true"""))
        assertTrue(body.contains(""""foodId":"f1""""))
        assertTrue(body.contains(""""labelId":"lab1""""))
    }

    @Test
    fun `deleting an item calls the documented endpoint`() = runTest {
        fake.enqueueJson("{}")
        repository.deleteItem("i1")
        val request = fake.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/households/shopping/items/i1", request.url.encodedPath)
    }

    @Test
    fun `creating a list posts its name`() = runTest {
        fake.enqueueJson("""{"id":"l9","name":"Semaine","listItems":[],"recipeReferences":[]}""")
        val result = repository.createList("Semaine")
        assertEquals("Semaine", (result as ApiResult.Success).value.name)
        assertTrue(fake.takeRequest().body?.utf8().orEmpty().contains(""""name":"Semaine""""))
    }

    @Test
    fun `pushing a recipe uses Mealie's own endpoint`() = runTest {
        fake.enqueueJson(LIST_DETAIL)

        repository.addRecipe(listId = "l1", recipeId = "r1", servings = 2.0)

        val request = fake.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/households/shopping/lists/l1/recipe/r1", request.url.encodedPath)
        assertTrue(request.body?.utf8().orEmpty().contains(""""recipeIncrementQuantity":2"""))
    }

    @Test
    fun `linked recipes are exposed with their quantity`() = runTest {
        fake.enqueueJson(LIST_DETAIL)
        val list = (repository.list("l1") as ApiResult.Success).value
        assertEquals(1, list.linkedRecipes.size)
        assertEquals("Poulet au curry", list.linkedRecipes.single().name)
    }

    @Test
    fun `a rejected token surfaces as an authentication error`() = runTest {
        fake.enqueueError(401)
        assertEquals(NetworkError.Unauthorized, (repository.lists() as ApiResult.Failure).error)
    }

    @Test
    fun `without an instance the lists cannot be read`() = runTest {
        val offline = ShoppingRepository { null }
        assertEquals(NetworkError.Unauthorized, (offline.lists() as ApiResult.Failure).error)
    }

    private companion object {
        val LISTS = """
        {
          "page":1,"per_page":50,"total":2,"total_pages":1,
          "items":[
            {"id":"l1","name":"Cellier","groupId":"g","userId":"u","householdId":"h","recipeReferences":[]},
            {"id":"l2","name":"Habituels","groupId":"g","userId":"u","householdId":"h","recipeReferences":[]}
          ]
        }
        """.trimIndent()

        val LIST_DETAIL = """
        {
          "id":"l1","name":"Cellier","groupId":"g","userId":"u","householdId":"h",
          "listItems":[
            {"id":"i3","shoppingListId":"l1","display":"Sel","note":"Sel","checked":true,"position":2},
            {"id":"i1","shoppingListId":"l1","display":"2 citrons","note":"citrons","quantity":2,
             "checked":false,"position":0,"foodId":"f1","labelId":"lab1",
             "label":{"id":"lab1","name":"Fruits","color":"#e11a1f","groupId":"g"}},
            {"id":"i2","shoppingListId":"l1","display":"Huile d'olive","checked":false,"position":1}
          ],
          "recipeReferences":[
            {"id":"ref1","shoppingListId":"l1","recipeId":"r1","recipeQuantity":1,
             "recipe":{"id":"r1","name":"Poulet au curry","slug":"poulet-au-curry"}}
          ]
        }
        """.trimIndent()
    }
}
