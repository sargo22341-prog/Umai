package org.opensources.umai.shopping.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.shopping.data.ShoppingRepository
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingViewModelTest {

    private lateinit var fake: FakeMealieServer

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        fake = FakeMealieServer()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        fake.shutdown()
    }

    private fun viewModel(listId: String? = null) = ShoppingViewModel(ShoppingRepository { fake.api() }, listId)

    private suspend fun ShoppingViewModel.awaitLists(): ShoppingUiState =
        withTimeout(TIMEOUT_MS) { state.first { !it.loadingLists } }

    private suspend fun ShoppingViewModel.awaitList(): ShoppingUiState =
        withTimeout(TIMEOUT_MS) { state.first { !it.loadingLists && it.list != null } }

    @Test
    fun `the first list is selected and opened automatically`() = runBlocking {
        fake.enqueueJson(LISTS)
        fake.enqueueJson(LIST_DETAIL)

        val state = viewModel().awaitList()

        assertEquals(2, state.lists.size)
        assertEquals("l1", state.selectedListId)
        assertEquals("Cellier", state.list?.name)
        assertEquals(2, state.list?.items?.size)
    }

    @Test
    fun `an instance with no list shows the empty state`() = runBlocking {
        fake.enqueueJson("""{"page":1,"per_page":50,"total":0,"total_pages":0,"items":[]}""")

        val state = viewModel().awaitLists()

        assertTrue(state.hasNoList)
        assertTrue(state.lists.isEmpty())
    }

    @Test
    fun `an empty list reports itself as empty rather than as an error`() = runBlocking {
        fake.enqueueJson(LISTS)
        fake.enqueueJson("""{"id":"l1","name":"Cellier","listItems":[],"recipeReferences":[]}""")

        val state = viewModel().awaitList()

        assertTrue(state.isListEmpty)
        assertEquals(null, state.error)
    }

    @Test
    fun `ticking an item updates the screen before the server confirms`() = runBlocking {
        fake.enqueueJson(LISTS)
        fake.enqueueJson(LIST_DETAIL)
        val vm = viewModel()
        val loaded = vm.awaitList()

        val item = loaded.list!!.items.first { !it.checked }
        fake.enqueueJson("""{"createdItems":[],"updatedItems":[],"deletedItems":[]}""")
        fake.enqueueJson(LIST_DETAIL)

        vm.setChecked(item, checked = true)

        val updated = withTimeout(TIMEOUT_MS) {
            vm.state.first { state -> state.list?.items?.any { it.id == item.id && it.checked } == true }
        }
        assertTrue(updated.list!!.items.first { it.id == item.id }.checked)
    }

    @Test
    fun `a refused update puts the item back and reports the error`() = runBlocking {
        fake.enqueueJson(LISTS)
        fake.enqueueJson(LIST_DETAIL)
        val vm = viewModel()
        val loaded = vm.awaitList()
        val item = loaded.list!!.items.first { !it.checked }

        fake.enqueueError(500)
        vm.setChecked(item, checked = true)

        val state = withTimeout(TIMEOUT_MS) { vm.state.first { it.error != null } }
        assertEquals(NetworkError.Server(500), state.error)
        assertFalse(state.list!!.items.first { it.id == item.id }.checked)
    }

    @Test
    fun `the shopping mode opens the list it was asked for`() = runBlocking {
        fake.enqueueJson(LISTS)
        fake.enqueueJson(LIST_DETAIL.replace("\"id\":\"l1\",\"name\":\"Cellier\"", "\"id\":\"l2\",\"name\":\"Habituels\""))

        val state = viewModel(listId = "l2").awaitList()

        assertEquals("l2", state.selectedListId)
        assertEquals("Habituels", state.list?.name)
        fake.takeRequest()
        assertTrue(fake.takeRequest().url.encodedPath.endsWith("/l2"))
    }

    @Test
    fun `the items to buy and those in the basket are told apart`() = runBlocking {
        fake.enqueueJson(LISTS)
        fake.enqueueJson(LIST_DETAIL)

        val state = viewModel().awaitList()

        assertEquals(listOf("i1"), state.remainingItems.map { it.id })
        assertEquals(listOf("i2"), state.basketItems.map { it.id })
    }

    @Test
    fun `a list read while a tick is on its way keeps the item ticked`() = runBlocking {
        var serverList = LIST_DETAIL
        val confirm = CountDownLatch(1)
        // Routed by request, since the tick and the reading run side by side.
        fake.server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.method == "PUT" -> {
                    confirm.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    json("""{"createdItems":[],"updatedItems":[],"deletedItems":[]}""")
                }
                request.url.encodedPath.endsWith("/lists") -> json(LISTS)
                else -> json(serverList)
            }
        }
        val vm = viewModel()
        val item = vm.awaitList().list!!.items.first { it.id == "i1" }

        // Mealie has not confirmed the tick yet when the list is read again.
        vm.setChecked(item, checked = true)
        vm.selectList("l1")
        val reread = withTimeout(TIMEOUT_MS) { vm.state.first { !it.loadingList } }
        assertTrue(reread.list!!.items.first { it.id == "i1" }.checked)

        // Once confirmed, the list is read from Mealie again.
        serverList = LIST_DETAIL.replace("\"checked\":false", "\"checked\":true").replace("2 citrons", "3 citrons")
        confirm.countDown()
        val confirmed = withTimeout(TIMEOUT_MS) {
            vm.state.first { state -> state.list?.items?.any { it.display == "3 citrons" } == true }
        }
        assertTrue(confirmed.list!!.items.first { it.id == "i1" }.checked)
    }

    private fun json(body: String) = MockResponse.Builder()
        .setHeader("Content-Type", "application/json")
        .body(body)
        .build()

    @Test
    fun `an unreachable instance surfaces the error`() = runBlocking {
        fake.enqueueError(503)

        val state = viewModel().awaitLists()

        assertEquals(NetworkError.Server(503), state.error)
    }

    @Test
    fun `a blank item is never sent to the server`() = runBlocking {
        fake.enqueueJson(LISTS)
        fake.enqueueJson(LIST_DETAIL)
        val vm = viewModel()
        vm.awaitList()
        val before = fake.server.requestCount

        vm.addItem("   ")

        assertEquals(before, fake.server.requestCount)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L

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
            {"id":"i1","shoppingListId":"l1","display":"2 citrons","note":"citrons","quantity":2,
             "checked":false,"position":0},
            {"id":"i2","shoppingListId":"l1","display":"Sel","note":"Sel","checked":true,"position":1}
          ],
          "recipeReferences":[]
        }
        """.trimIndent()
    }
}
