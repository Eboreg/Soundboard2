package us.huseli.soundboard2.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.data.repositories.StateRepository
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.helpers.SoundSorting
import javax.inject.Inject

@HiltViewModel
class CategoryAddViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val stateRepository: StateRepository
) : BaseCategoryEditViewModel, LoggingObject, ViewModel() {

    private var _name: CharSequence = ""
    private val _selectedBackgroundColor = MutableStateFlow<Int?>(null)
    private var _sortOrder: SoundSorting.Order = SoundSorting.Order.ASCENDING
    private var _sortParameter: SoundSorting.Parameter = SoundSorting.Parameter.NAME
    private val _backgroundColor: Flow<Int> = merge(
        categoryRepository.randomColor,
        _selectedBackgroundColor.filterNotNull()
    )

    val name: CharSequence
        get() = _name.trim()

    val soundSorting: SoundSorting
        get() = SoundSorting(_sortParameter, _sortOrder)

    val sortOrderAscending: Boolean
        get() = _sortOrder == SoundSorting.Order.ASCENDING

    override val backgroundColor: LiveData<Int> = _backgroundColor.asLiveData()
    override fun setBackgroundColor(color: Int) { _selectedBackgroundColor.value = color }

    fun setSortParameter(value: SoundSorting.Parameter) { _sortParameter = value }
    fun setSortOrder(value: SoundSorting.Order) { _sortOrder = value }
    fun setName(value: CharSequence) { _name = value }

    fun reset() = viewModelScope.launch {
        _name = ""
        _selectedBackgroundColor.value = null
        _sortOrder = SoundSorting.Order.ASCENDING
        _sortParameter = SoundSorting.Parameter.NAME
    }

    fun save() = viewModelScope.launch {
        val backgroundColor = _backgroundColor.stateIn(viewModelScope).value
        if (name != "") {
            categoryRepository.create(name, backgroundColor, soundSorting)
            stateRepository.push()
        }
    }
}