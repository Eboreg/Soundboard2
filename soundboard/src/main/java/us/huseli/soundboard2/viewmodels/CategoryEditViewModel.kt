package us.huseli.soundboard2.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.data.repositories.StateRepository
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.helpers.SoundSorting
import javax.inject.Inject

@HiltViewModel
class CategoryEditViewModel @Inject constructor(
    private val repository: CategoryRepository,
    private val stateRepository: StateRepository,
) : LoggingObject, BaseCategoryEditViewModel, ViewModel() {

    private val _categoryId = MutableStateFlow<Int?>(null)
    @OptIn(ExperimentalCoroutinesApi::class)
    private val _category: Flow<Category?> = _categoryId.flatMapLatest { categoryId ->
        if (categoryId != null) repository.get(categoryId) else flowOf(null)
    }
    private val _sortOrder = MutableStateFlow<SoundSorting.Order?>(null)
    private val _sortParameter = MutableStateFlow<SoundSorting.Parameter?>(null)
    private val _newBackgroundColor = MutableStateFlow<Int?>(null)
    private val _newName = MutableStateFlow<CharSequence?>(null)

    private val _newSoundSorting: Flow<SoundSorting> =
        combine(_sortParameter.filterNotNull(), _sortOrder.filterNotNull()) { parameter, order ->
            log("_newSoundSorting: parameter=$parameter, order=$order")
            SoundSorting(parameter, order)
        }

    private val _soundSorting = merge(
        _category.filterNotNull().map {
            log("_soundSorting: _category=$it, _category.soundSorting=${it.soundSorting}")
            it.soundSorting
        },
        _newSoundSorting
    )

    val selectedSortParameterPosition: LiveData<Int> =
        _soundSorting.map {
            val position = SoundSorting.sortParameters.indexOf(it.parameter)
            log("selectedSortParameterPosition: _soundSorting=$it, position=$position")
            position
        }.asLiveData()

    val sortOrderAscending: LiveData<Boolean> =
        _soundSorting.map { it.order == SoundSorting.Order.ASCENDING }.asLiveData()

    val soundSorting: LiveData<SoundSorting> = _soundSorting.asLiveData()

    override val backgroundColor: LiveData<Int> = merge(
        _category.mapNotNull { it?.backgroundColor },
        _newBackgroundColor.filterNotNull()
    ).asLiveData()

    val name: LiveData<CharSequence> = merge(
        _category.mapNotNull { it?.name },
        _newName.filterNotNull()
    ).asLiveData()

    override fun setBackgroundColor(color: Int) {
        _newBackgroundColor.value = color
    }

    fun setCategoryId(value: Int) {
        _categoryId.value = value
    }

    fun setName(value: CharSequence) {
        _newName.value = value
    }

    fun setSortOrder(value: SoundSorting.Order) {
        log("setSortOrder: value=$value")
        _sortOrder.value = value
    }

    fun setSortParameter(value: SoundSorting.Parameter) {
        log("setSortParameter: value=$value")
        _sortParameter.value = value
    }

    fun reset() {
        _categoryId.value = null
        _newBackgroundColor.value = null
        _newName.value = null
        _sortOrder.value = null
        _sortParameter.value = null
    }

    fun save(name: CharSequence) = viewModelScope.launch {
        val oldCategory = _category.stateIn(viewModelScope).value
        if (oldCategory != null) {
            val newCategory = oldCategory.clone(
                name = name,
                backgroundColor = _newBackgroundColor.value,
                soundSorting = _soundSorting.stateIn(viewModelScope).value
            )
            if (!oldCategory.isIdenticalTo(newCategory)) {
                repository.update(newCategory)
                stateRepository.push()
            }
        }
    }
}