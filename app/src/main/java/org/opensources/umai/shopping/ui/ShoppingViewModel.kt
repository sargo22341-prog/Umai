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
    val error: NetworkError? = null,
) {
    val hasNoList: Boolean get() = !loadingLists && lists.isEmpty()
    val isListEmpty: Boolean get() = list?.items?.isEmpty() == true && !loadingList
}

class ShoppingViewModel(private val repository: ShoppingRepository) : ViewModel() {

    private val _state = MutableStateFlow(ShoppingUiState())
    val state: StateFlow<ShoppingUiState> = _state.asStateFlow()

    init {
        loadLists()
    }

    fun loadLists() {
        _state.update { it.copy(loadingLists = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.lists()) {
                is ApiResult.Failure ->
                    _state.update { it.copy(loadingLists = false, error = result.error) }
                is ApiResult.Success -> {
                    val lists = result.value
                    val selected = _state.value.selectedListId?.takeIf { id ->
                        lists.any { it.id == id }
                    } ?: lists.firstOrNull()?.id
                    _state.update {
                        it.copy(lists = lists, selectedListId = selected, loadingLists = false, error = null)
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
                    _state.update { it.copy(list = result.value, loadingList = false, error = null) }
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
        updateLocally(item.copy(checked = checked))
        viewModelScope.launch {
            when (val result = repository.updateItem(item.copy(checked = checked))) {
                is ApiResult.Failure -> {
                    updateLocally(item)
                    _state.update { it.copy(error = result.error) }
                }
                is ApiResult.Success -> selectList(listId)
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
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { ShoppingViewModel(container.shoppingRepository) }
        }
    }
}
