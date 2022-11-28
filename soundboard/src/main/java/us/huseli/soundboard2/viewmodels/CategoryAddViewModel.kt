package us.huseli.soundboard2.viewmodels

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.data.repositories.StateRepository
import us.huseli.soundboard2.helpers.LoggingObject
import javax.inject.Inject

@HiltViewModel
class CategoryAddViewModel @Inject constructor(
    categoryRepository: CategoryRepository,
    private val stateRepository: StateRepository
) : BaseCategoryEditViewModel(categoryRepository), LoggingObject {
    fun initialize() {
        isReadyInternal.value = false
        name.value = ""
        sortOrderAscending.value = true
        sortOrderDescending.value = false
        sortParameterPosition.value = 0

        viewModelScope.launch(Dispatchers.IO) {
            setRandomBackgroundColorInternal()
            isReadyInternal.value = true
        }
    }

    override fun save() {
        viewModelScope.launch(Dispatchers.IO) {
            categoryRepository.create(name.value.trim(), backgroundColor.value, soundSortingInternal)
            stateRepository.push()
        }
    }
}