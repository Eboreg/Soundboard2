package us.huseli.soundboard2.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.helpers.LoggingObject
import javax.inject.Inject

@HiltViewModel
class CategoryAddViewModel @Inject constructor(private val categoryRepository: CategoryRepository) :
    BaseCategoryEditViewModel, LoggingObject, ViewModel() {

    private val _name = MutableStateFlow<CharSequence>("")
    private val _selectedBackgroundColor = MutableStateFlow<Int?>(null)

    override val backgroundColor: LiveData<Int> = merge(
        categoryRepository.randomColor,
        _selectedBackgroundColor.filterNotNull()
    ).asLiveData()

    val name: LiveData<CharSequence>
        get() = _name.asLiveData()

    fun setName(value: CharSequence) {
        _name.value = value
    }

    override fun setBackgroundColor(color: Int) {
        log("setBackgroundColor(): value=$color")
        _selectedBackgroundColor.value = color
    }

    fun reset() = viewModelScope.launch {
        _name.value = ""
        categoryRepository.randomColor.collect { setBackgroundColor(it) }
    }

    fun save() = viewModelScope.launch {
        val backgroundColor = backgroundColor.value
        val name = _name.value
        log("save(): name=$name, backgroundColor=$backgroundColor")
        if (backgroundColor != null && name != "") categoryRepository.create(name, backgroundColor)
    }
}