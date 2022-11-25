package us.huseli.soundboard2.viewmodels

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.data.repositories.StateRepository
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.helpers.SoundSorting
import javax.inject.Inject

@HiltViewModel
class CategoryEditViewModel @Inject constructor(
    categoryRepository: CategoryRepository,
    private val stateRepository: StateRepository,
) : LoggingObject, BaseCategoryEditViewModel(categoryRepository) {
    private var categoryIdInternal: Int? = null

    fun initialize(categoryId: Int) {
        categoryIdInternal = categoryId
        isReadyInternal.value = false

        viewModelScope.launch {
            val category = categoryRepository.get(categoryId)
            name.value = category.name
            backgroundColor.value = category.backgroundColor
            sortParameterPosition.value = SoundSorting.Parameter.values().indexOf(category.soundSorting.parameter)
            sortOrderAscending.value = category.soundSorting.order == SoundSorting.Order.ASCENDING
            sortOrderDescending.value = category.soundSorting.order == SoundSorting.Order.DESCENDING
            isReadyInternal.value = true
        }
    }

    override fun save() {
        categoryIdInternal?.let { categoryId ->
            viewModelScope.launch {
                val oldCategory = categoryRepository.get(categoryId)
                val newCategory = oldCategory.clone(
                    name = name.value.trim(),
                    backgroundColor = backgroundColor.value,
                    soundSorting = soundSortingInternal
                )
                if (!oldCategory.isIdenticalTo(newCategory)) {
                    categoryRepository.update(newCategory)
                    stateRepository.push()
                }
            }
        }
    }
}