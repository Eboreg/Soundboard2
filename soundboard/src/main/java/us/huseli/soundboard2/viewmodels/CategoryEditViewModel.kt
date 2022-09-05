package us.huseli.soundboard2.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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

    private var _category = MutableStateFlow<Category?>(null)
    private val _sortOrder = MutableStateFlow<SoundSorting.Order?>(null)
    private val _sortParameter = MutableStateFlow<SoundSorting.Parameter?>(null)
    private val _newBackgroundColor = MutableStateFlow<Int?>(null)
    private val _newName = MutableStateFlow<CharSequence?>(null)

    private val __soundSorting = merge(
        _category.mapNotNull {
            log("_soundSorting: _category=$it, _category.soundSorting=${it?.soundSorting}")
            it?.soundSorting
        },
        combine(_sortOrder.filterNotNull(), _sortParameter.filterNotNull()) { order, parameter ->
            log("_soundSorting: _sortOrder=$order, _sortParameter=$parameter")
            SoundSorting(parameter, order)
        }
    )

    private val _soundSorting = combine(_category.filterNotNull(), _sortParameter, _sortOrder) { category, parameter, order ->
        log("_soundSorting: _sortOrder=$order, _sortParameter=$parameter, _category=$category")
        if (parameter != null && order != null) SoundSorting(parameter, order)
        else category.soundSorting
    }

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

    fun setCategory(value: Category) {
        // _categoryId.value = value
        _category.value = value
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