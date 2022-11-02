package us.huseli.soundboard2.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.data.repositories.StateRepository
import us.huseli.soundboard2.helpers.LoggingObject
import javax.inject.Inject

@HiltViewModel
class CategoryDeleteViewModel @Inject constructor(
    private val repository: CategoryRepository,
    private val stateRepository: StateRepository
) : LoggingObject, ViewModel() {
    private val _categoryId = MutableStateFlow<Int?>(null)
    @OptIn(ExperimentalCoroutinesApi::class)
    private val _category: Flow<Category?> = _categoryId.filterNotNull().flatMapLatest { repository.get(it) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _otherCategories: Flow<List<Category>> = _categoryId.flatMapLatest { categoryId ->
        if (categoryId != null)
            repository.categories.map { list -> list.filter { it.id != categoryId } }
        else emptyFlow()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _soundCount: Flow<Int> = _categoryId.flatMapLatest { categoryId ->
        if (categoryId != null) repository.getSoundCount(categoryId) else emptyFlow()
    }

    private val _isLastCategory = merge(
        flowOf(false),
        _otherCategories.map { it.isEmpty() },
    )

    private val _showSoundAction = merge(
        flowOf(false),
        combine(_soundCount, _isLastCategory) { sc, ilc -> sc > 0 && !ilc }
    )

    val name: LiveData<String> = _category.map { it?.name ?: "" }.asLiveData()
    val otherCategories: LiveData<List<Category>> = _otherCategories.asLiveData()
    val soundCount: LiveData<Int> = _soundCount.asLiveData()
    val isLastCategory: LiveData<Boolean> = _isLastCategory.asLiveData()
    val showSoundAction: LiveData<Boolean> = _showSoundAction.asLiveData()

    fun setCategoryId(value: Int) {
        _categoryId.value = value
    }

    fun delete(moveSoundsTo: Int?) = viewModelScope.launch {
        _category.stateIn(viewModelScope).value?.let { category ->
            log("delete(): category=$category, moveSoundsTo=$moveSoundsTo")
            repository.delete(category, moveSoundsTo)
            stateRepository.push()
        }
    }
}