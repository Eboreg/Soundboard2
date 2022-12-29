package us.huseli.soundboard2.viewmodels

import androidx.annotation.ColorInt
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.helpers.ColorHelper
import javax.inject.Inject

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val repository: CategoryRepository,
    colorHelper: ColorHelper
) : ViewModel() {
    private val _categoryId = MutableStateFlow<Int?>(null)
    private val _category = combine(_categoryId, repository.categories) { categoryId, categories ->
        categoryId?.let { categories.find { it.id == categoryId } }
    }.filterNotNull()

    @ColorInt
    private val _backgroundColor = _category.map { it.backgroundColor }

    @ColorInt
    val backgroundColor: LiveData<Int> = _backgroundColor.asLiveData()
    val collapseIconRotation: LiveData<Float> = _category.map { if (it.collapsed) -90f else 0f }.asLiveData()
    val isMoveDownPossible: LiveData<Boolean> = _category.map { !it.isLast }.asLiveData()
    val isMoveUpPossible: LiveData<Boolean> = _category.map { !it.isFirst }.asLiveData()
    val name: LiveData<String> = _category.map { it.name }.asLiveData()
    @ColorInt
    val textColor: LiveData<Int> = _backgroundColor.map { colorHelper.getColorOnBackground(it) }.asLiveData()

    fun setCategoryId(categoryId: Int) {
        _categoryId.value = categoryId
    }

    fun toggleCollapsed() = viewModelScope.launch(Dispatchers.IO) {
        _categoryId.value?.let { categoryId -> repository.toggleCollapsed(categoryId) }
    }

    /** Switch places of this category and the next one, if any. */
    fun moveDown() = viewModelScope.launch(Dispatchers.IO) {
        val categories = repository.categories.stateIn(viewModelScope).value
        val idx = categories.indexOfFirst { it.id == _categoryId.value }
        // Double check so it's not already the last category:
        if (idx != -1 && idx < categories.size - 1) {
            repository.update(
                categories[idx].clone(position = categories[idx].position + 1),
                categories[idx + 1].clone(position = categories[idx + 1].position - 1),
            )
        }
    }

    /** Switch places of this category and the previous one, if any. */
    fun moveUp() = viewModelScope.launch(Dispatchers.IO) {
        val categories = repository.categories.stateIn(viewModelScope).value
        val idx = categories.indexOfFirst { it.id == _categoryId.value }
        // Double check so it's not already the first category:
        if (idx > 0) {
            repository.update(
                categories[idx].clone(position = categories[idx].position - 1),
                categories[idx - 1].clone(position = categories[idx - 1].position + 1),
            )
        }
    }
}