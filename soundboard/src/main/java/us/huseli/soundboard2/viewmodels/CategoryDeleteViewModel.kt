package us.huseli.soundboard2.viewmodels

import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.entities.CategoryDeleteData
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.helpers.LoggingObject
import javax.inject.Inject

@HiltViewModel
class CategoryDeleteViewModel @Inject constructor(
    private val repository: CategoryRepository
) : LoggingObject, ViewModel() {
    private val _categoryId = MutableStateFlow<Int?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _categoryData: Flow<CategoryDeleteData> = _categoryId.flatMapLatest { categoryId ->
        if (categoryId != null) repository.getCategoryDeleteData(categoryId) else emptyFlow()
    }.filterNotNull()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _otherCategories: Flow<List<Category>> = _categoryId.flatMapLatest { categoryId ->
        if (categoryId != null)
            repository.categories.map { list -> list.filter { it.id != categoryId } }
        else emptyFlow()
    }

    private val _soundCount: Flow<Int> = _categoryData.map { it.soundCount ?: 0 }

    private val _isLastCategory = merge(
        flowOf(false),
        _otherCategories.map { it.isEmpty() },
    )

    private val _showSoundAction = merge(
        flowOf(false),
        combine(_categoryData, _isLastCategory) { cd, ilc -> (cd.soundCount ?: 0) > 0 && !ilc }
    )

    val name: LiveData<String> = _categoryData.map { it.name ?: "" }.asLiveData()
    val otherCategories: LiveData<List<Category>> = _otherCategories.asLiveData()
    val soundCount: LiveData<Int> = _soundCount.asLiveData()
    val isLastCategory: LiveData<Boolean> = _isLastCategory.asLiveData()
    val showSoundAction: LiveData<Boolean> = _showSoundAction.asLiveData()

    fun setCategoryId(value: Int) {
        _categoryId.value = value
    }

    fun delete(moveSoundsTo: Int?) = viewModelScope.launch {
        _categoryId.value?.let { categoryId ->
            log("delete(): categoryId=$categoryId, moveSoundsTo=$moveSoundsTo")
            repository.delete(categoryId, moveSoundsTo)
        }
    }
}