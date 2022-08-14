package us.huseli.soundboard2.viewmodels

import androidx.lifecycle.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.helpers.ColorHelper

class CategoryViewModel(
    private val repository: CategoryRepository,
    colorHelper: ColorHelper,
    private val categoryId: Int
) : ViewModel() {
    private val _category: Flow<Category?> = repository.getCategory(categoryId)
    private val _moveButtonsVisible = MutableStateFlow(false)

    val soundIds: LiveData<List<Int>> = repository.getSoundIds(categoryId).asLiveData()
    val backgroundColor: LiveData<Int?> = _category.map { it?.backgroundColor }.asLiveData()
    val textColor: LiveData<Int?> = backgroundColor.map { it?.let { colorHelper.getColorOnBackground(it) } }
    val name: LiveData<String?> = _category.map { it?.name }.asLiveData()
    val collapseIconRotation: LiveData<Float> = _category.map { if (it?.collapsed == true) -90f else 0f }.asLiveData()
    val soundListVisible: LiveData<Boolean> = _category.map { it?.collapsed != true }.asLiveData()

    val moveButtonsVisible: LiveData<Boolean>
        get() = _moveButtonsVisible.asLiveData()

    fun toggleCollapsed() = viewModelScope.launch { repository.toggleCategoryCollapsed(categoryId) }

    class Factory(private val repository: CategoryRepository, private val colorHelper: ColorHelper, private val categoryId: Int) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CategoryViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return CategoryViewModel(repository, colorHelper, categoryId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}