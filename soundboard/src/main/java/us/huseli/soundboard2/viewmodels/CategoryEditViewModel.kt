package us.huseli.soundboard2.viewmodels

import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.helpers.SoundSorting
import javax.inject.Inject

@HiltViewModel
class CategoryEditViewModel @Inject constructor(private val repository: CategoryRepository) :
    LoggingObject, BaseCategoryEditViewModel, ViewModel() {

    private val _sortOrder = MutableStateFlow<SoundSorting.Order?>(null)
    private val _sortParameter = MutableStateFlow<SoundSorting.Parameter?>(null)
    private val _categoryId = MutableStateFlow<Int?>(null)
    private val _newBackgroundColor = MutableStateFlow<Int?>(null)
    private val _newName = MutableStateFlow<CharSequence?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _category: Flow<Category?> = _categoryId.flatMapLatest {
        if (it != null) repository.get(it) else emptyFlow()
    }

    private val _soundSorting = merge(
        _category.mapNotNull {
            log("_soundSorting: _category=$it, _category.soundSorting=${it?.soundSorting}")
            it?.soundSorting
        },
        combine(_sortOrder.filterNotNull(), _sortParameter.filterNotNull()) { order, parameter ->
            log("_soundSorting: _sortOrder=$order, _sortParameter=$parameter")
            SoundSorting(parameter, order)
        }
    )

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
        _newBackgroundColor.value = null
        _newName.value = null
    }

    fun save(name: CharSequence) = viewModelScope.launch {
        val soundSorting = _soundSorting.stateIn(viewModelScope).value
        val category = _category.stateIn(viewModelScope).value?.clone(
            name = name,
            backgroundColor = _newBackgroundColor.value,
            soundSorting = soundSorting
        )
        log("save(): category=$category, soundSorting=$soundSorting, category.soundSorting=${category?.soundSorting}")
        if (category != null) repository.update(category)
    }
}