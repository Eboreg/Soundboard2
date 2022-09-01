package us.huseli.soundboard2.viewmodels

import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.helpers.SoundSorting
import javax.inject.Inject

@HiltViewModel
class CategoryEditViewModel @Inject constructor(private val repository: CategoryRepository) :
    BaseCategoryEditViewModel, ViewModel() {

    private var _sortOrder = SoundSorting.Order.ASCENDING
    private var _sortParameter = SoundSorting.Parameter.UNDEFINED

    private val _categoryId = MutableStateFlow<Int?>(null)
    private val _newBackgroundColor = MutableStateFlow<Int?>(null)
    private val _newName = MutableStateFlow<CharSequence?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _category: Flow<Category?> = _categoryId.flatMapLatest {
        if (it != null) repository.get(it) else emptyFlow()
    }

    val sortOrderAscending = _sortOrder == SoundSorting.Order.ASCENDING

    override val backgroundColor: LiveData<Int> = merge(
        _category.mapNotNull { it?.backgroundColor },
        _newBackgroundColor.filterNotNull()
    ).asLiveData()

    val name: LiveData<CharSequence> = merge(
        _category.mapNotNull { it?.name },
        _newName.filterNotNull()
    ).asLiveData()

    override fun setBackgroundColor(color: Int) { _newBackgroundColor.value = color }
    fun setCategoryId(value: Int) { _categoryId.value = value }
    fun setName(value: CharSequence) { _newName.value = value }
    fun setSortOrder(value: SoundSorting.Order) { _sortOrder = value }
    fun setSortParameter(value: SoundSorting.Parameter) { _sortParameter = value }

    fun reset() {
        _newBackgroundColor.value = null
        _newName.value = null
        _sortParameter = SoundSorting.Parameter.UNDEFINED
        _sortOrder = SoundSorting.Order.ASCENDING
    }

    fun save(name: CharSequence) = viewModelScope.launch {
        val category = _category.stateIn(viewModelScope).value?.clone(
            name = name,
            backgroundColor = _newBackgroundColor.value
        )

        if (category != null) {
            val soundSorting = SoundSorting(_sortParameter, _sortOrder)
            repository.update(category)
            if (soundSorting.parameter != SoundSorting.Parameter.UNDEFINED)
                repository.sortSounds(category.id, soundSorting)
        }
    }
}