package org.opensources.umai.shopping.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.opensources.umai.core.di.AppContainer
import org.opensources.umai.core.model.ShoppingItem
import org.opensources.umai.core.model.ShoppingList
import org.opensources.umai.core.model.ShoppingListSummary
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.shopping.data.ShoppingRepository

data class ShoppingUiState(
    val lists: List<ShoppingListSummary> = emptyList(),
    val selectedListId: String? = null,
    val list: ShoppingList? = null,
    val loadingLists: Boolean = true,
    val loadingList: Boolean = false,
    val refreshing: Boolean = false,
    val error: NetworkError? = null,
) {
    val hasNoList: Boolean get() = !loadingLists && lists.isEmpty()
    val isListEmpty: Boolean get() = list?.items?.isEmpty() == true && !loadingList

    /** Items still to buy, and those already in the basket, for the shopping mode. */
    val remainingItems: List<ShoppingItem> get() = list?.items.orEmpty().filterNot { it.checked }
    val basketItems: List<ShoppingItem> get() = list?.items.orEmpty().filter { it.checked }
}

/** [initialListId] opens that list rather than the first one, as the shopping mode does. */
class ShoppingViewModel(
    private val repository: ShoppingRepository,
    initialListId: String? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(ShoppingUiState(selectedListId = initialListId))
    val state: StateFlow<ShoppingUiState> = _state.asStateFlow()

    private var hasLoadedOnce = false

    /**
     * Ticks sent to Mealie and not confirmed yet, by item id. A list read in the
     * meantime would show them unticked again; they are laid over it until
     * Mealie confirms, so items ticked in a row never flicker back.
     */
    private val pendingChecks = mutableMapOf<String, Boolean>()

    init {
        loadLists()
    }

    /**
     * Called every time the tab comes back into view.
     *
     * A list can have grown while the user was on a recipe page — or in the
     * Mealie web UI — and the ViewModel outlives the screen, so coming back has
     * to ask the server again. The very first display is already covered by the
     * initial load.
     */
    fun onScreenShown() {
        if (hasLoadedOnce) refresh()
    }

    fun refresh() = loadLists(refreshing = true)

    fun loadLists() = loadLists(refreshing = false)

    private fun loadLists(refreshing: Boolean) {
        _state.update {
            it.copy(loadingLists = !refreshing, refreshing = refreshing, error = null)
        }
        viewModelScope.launch {
            when (val result = repository.lists()) {
                is ApiResult.Failure -> _state.update {
                    it.copy(loadingLists = false, refreshing = false, error = result.error)
                }
                is ApiResult.Success -> {
                    hasLoadedOnce = true
                    val lists = result.value
                    val selected = _state.value.selectedListId?.takeIf { id ->
                        lists.any { it.id == id }
                    } ?: lists.firstOrNull()?.id
                    _state.update {
                        it.copy(
                            lists = lists,
                            selectedListId = selected,
                            loadingLists = false,
                            refreshing = false,
                            error = null,
                        )
                    }
                    selected?.let { selectList(it) }
                }
            }
        }
    }

    fun selectList(id: String) {
        _state.update { it.copy(selectedListId = id, loadingList = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.list(id)) {
                is ApiResult.Failure ->
                    _state.update { it.copy(loadingList = false, error = result.error) }
                is ApiResult.Success ->
                    _state.update { it.copy(list = result.value.withPendingChecks(), loadingList = false, error = null) }
            }
        }
    }

    fun createList(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            when (val result = repository.createList(name.trim())) {
                is ApiResult.Failure -> _state.update { it.copy(error = result.error) }
                is ApiResult.Success -> {
                    _state.update { it.copy(selectedListId = result.value.id) }
                    loadLists()
                }
            }
        }
    }

    fun deleteList(id: String) {
        viewModelScope.launch {
            when (val result = repository.deleteList(id)) {
                is ApiResult.Failure -> _state.update { it.copy(error = result.error) }
                is ApiResult.Success -> {
                    _state.update { it.copy(selectedListId = null, list = null) }
                    loadLists()
                }
            }
        }
    }

    fun addItem(note: String) {
        val listId = _state.value.selectedListId ?: return
        if (note.isBlank()) return
        val position = _state.value.list?.items?.maxOfOrNull { it.position }?.plus(1) ?: 0
        viewModelScope.launch {
            when (val result = repository.addItem(listId, note.trim(), quantity = 1.0, position = position)) {
                is ApiResult.Failure -> _state.update { it.copy(error = result.error) }
                is ApiResult.Success -> selectList(listId)
            }
        }
    }

    /**
     * Ticking an item is applied locally first so the list stays responsive,
     * then confirmed against Mealie; a failure restores the previous value.
     */
    fun setChecked(item: ShoppingItem, checked: Boolean) {
        val listId = _state.value.selectedListId ?: return
        pendingChecks[item.id] = checked
        updateLocally(item.copy(checked = checked))
        viewModelScope.launch {
            val result = repository.updateItem(item.copy(checked = checked))
            // The same item ticked again meanwhile: the newer tick decides.
            val superseded = pendingChecks[item.id] != checked
            if (!superseded) pendingChecks.remove(item.id)
            when (result) {
                is ApiResult.Failure -> {
                    if (!superseded) updateLocally(item)
                    _state.update { it.copy(error = result.error) }
                }
                // The list is read again once every tick sent is confirmed.
                is ApiResult.Success -> if (pendingChecks.isEmpty()) selectList(listId)
            }
        }
    }

    fun updateNote(item: ShoppingItem, note: String) {
        val listId = _state.value.selectedListId ?: return
        viewModelScope.launch {
            when (val result = repository.updateItem(item.copy(note = note.trim()))) {
                is ApiResult.Failure -> _state.update { it.copy(error = result.error) }
                is ApiResult.Success -> selectList(listId)
            }
        }
    }

    fun deleteItem(item: ShoppingItem) {
        val listId = _state.value.selectedListId ?: return
        viewModelScope.launch {
            when (val result = repository.deleteItem(item.id)) {
                is ApiResult.Failure -> _state.update { it.copy(error = result.error) }
                is ApiResult.Success -> selectList(listId)
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun ShoppingList.withPendingChecks(): ShoppingList =
        if (pendingChecks.isEmpty()) {
            this
        } else {
            copy(items = items.map { item -> pendingChecks[item.id]?.let { item.copy(checked = it) } ?: item })
        }

    private fun updateLocally(item: ShoppingItem) {
        _state.update { current ->
            val list = current.list ?: return@update current
            current.copy(
                list = list.copy(
                    items = list.items.map { if (it.id == item.id) item else it },
                ),
            )
        }
    }

    companion object {
        fun factory(container: AppContainer, listId: String? = null) = viewModelFactory {
            initializer { ShoppingViewModel(container.shoppingRepository, initialListId = listId) }
        }
    }
}
