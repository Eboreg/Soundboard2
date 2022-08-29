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

    private val _categoryId = MutableStateFlow<Int?>(null)
    private val _newBackgroundColor = MutableStateFlow<Int?>(null)
    private val _newName = MutableStateFlow<CharSequence?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val category: Flow<Category?> = _categoryId.flatMapLatest {
        if (it != null) repository.get(it) else emptyFlow()
    }

    val sortOrder = MutableLiveData(SoundSorting.Order.ASCENDING)
    val sortParameter = MutableLiveData(SoundSorting.Parameter.UNDEFINED)
    val sortOrderAscending: LiveData<Boolean> = sortOrder.map { it == SoundSorting.Order.ASCENDING }

    override val backgroundColor: LiveData<Int> = merge(
        category.mapNotNull { it?.backgroundColor },
        _newBackgroundColor.filterNotNull()
    ).asLiveData()

    val name: LiveData<CharSequence> = merge(
        category.mapNotNull { it?.name },
        _newName.filterNotNull()
    ).asLiveData()

    fun setCategoryId(value: Int) {
        _categoryId.value = value
    }

    override fun setBackgroundColor(color: Int) {
        _newBackgroundColor.value = color
    }

    fun setName(value: CharSequence) {
        _newName.value = value
    }

    fun reset() {
        _newBackgroundColor.value = null
        _newName.value = null
        sortParameter.value = SoundSorting.Parameter.UNDEFINED
        sortOrder.value = SoundSorting.Order.ASCENDING
    }

    fun save(name: CharSequence, soundSorting: SoundSorting) = viewModelScope.launch {
        _categoryId.value?.let { categoryId ->
            repository.update(categoryId, name, backgroundColor.value, soundSorting)
        }
    }
}