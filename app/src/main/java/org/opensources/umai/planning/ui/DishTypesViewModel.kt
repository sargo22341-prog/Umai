package org.opensources.umai.planning.ui

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
import org.opensources.umai.core.model.Organizer
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.organizer.data.OrganizerRepository
import org.opensources.umai.planning.data.DishCourses
import org.opensources.umai.planning.domain.CourseVocabulary
import org.opensources.umai.planning.domain.DishCourse
import org.opensources.umai.recipe.domain.withoutCalorieTags

/** A category or tag, the course its name gives, and the one the user chose. */
data class DishTypeRow(val organizer: Organizer, val isTag: Boolean, val detected: DishCourse?, val chosen: DishCourse?) {
    val effective: DishCourse? get() = chosen ?: detected
}

data class DishTypesUiState(
    val rows: List<DishTypeRow> = emptyList(),
    val loading: Boolean = true,
    val error: NetworkError? = null,
) {
    val isEmpty: Boolean get() = !loading && error == null && rows.isEmpty()
}

/**
 * The categories and tags of the instance with the course the automatic
 * planning sees in each: the user can correct it, for this device.
 */
class DishTypesViewModel(
    private val organizers: OrganizerRepository,
    private val courses: DishCourses,
) : ViewModel() {

    private val _state = MutableStateFlow(DishTypesUiState())
    val state: StateFlow<DishTypesUiState> = _state.asStateFlow()

    private var organizerRows: List<Pair<Organizer, Boolean>> = emptyList()
    private var chosen: Map<String, DishCourse> = emptyMap()

    init {
        load()
        viewModelScope.launch {
            courses.userCourses.collect { value ->
                chosen = value
                publish()
            }
        }
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val categories = organizers.categories()
            val tags = organizers.tags()
            val failure = listOf(categories, tags).firstNotNullOfOrNull { (it as? ApiResult.Failure)?.error }
            if (failure != null) {
                _state.update { it.copy(loading = false, error = failure) }
                return@launch
            }
            organizerRows = (categories as ApiResult.Success).value.map { it to false } +
                (tags as ApiResult.Success).value.withoutCalorieTags().map { it to true }
            _state.update { it.copy(loading = false) }
            publish()
        }
    }

    fun choose(organizerId: String, course: DishCourse?) {
        viewModelScope.launch { courses.setUserCourse(organizerId, course) }
    }

    private fun publish() = _state.update {
        it.copy(
            rows = organizerRows.map { (organizer, isTag) ->
                DishTypeRow(organizer, isTag, CourseVocabulary.ofOrganizer(organizer.name), chosen[organizer.id])
            },
        )
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { DishTypesViewModel(container.organizerRepository, container.dishCourseStore) }
        }
    }
}
