package us.huseli.soundboard2.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.helpers.LoggingObject
import javax.inject.Inject

@HiltViewModel
class CategoryAddViewModel @Inject constructor(private val categoryRepository: CategoryRepository) :
    BaseCategoryEditViewModel, LoggingObject, ViewModel() {

    private var _name: CharSequence = ""
    private val _selectedBackgroundColor = MutableStateFlow<Int?>(null)
    private val _backgroundColor: Flow<Int> = merge(
        categoryRepository.randomColor,
        _selectedBackgroundColor.filterNotNull()
    )

    val name: CharSequence
        get() = _name

    override val backgroundColor: LiveData<Int> = _backgroundColor.asLiveData()
    override fun setBackgroundColor(color: Int) { _selectedBackgroundColor.value = color }

    fun setName(value: CharSequence) { _name = value }

    fun reset() = viewModelScope.launch {
        _name = ""
        _selectedBackgroundColor.value = null
    }

    fun save() = viewModelScope.launch {
        val backgroundColor = _backgroundColor.stateIn(viewModelScope).value
        if (_name != "") categoryRepository.create(_name, backgroundColor)
    }
}