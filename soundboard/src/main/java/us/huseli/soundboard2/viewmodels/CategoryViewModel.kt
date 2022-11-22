package us.huseli.soundboard2.viewmodels

import androidx.annotation.ColorInt
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.data.repositories.SettingsRepository
import us.huseli.soundboard2.data.repositories.SoundRepository
import us.huseli.soundboard2.helpers.ColorHelper

class CategoryViewModel(
    private val repository: CategoryRepository,
    soundRepository: SoundRepository,
    settingsRepository: SettingsRepository,
    colorHelper: ColorHelper,
    val categoryId: Int
) : ViewModel() {
    private val _category = repository.flowGet(categoryId).filterNotNull()

    @ColorInt
    private val _backgroundColor = _category.map { it.backgroundColor }

    @ColorInt
    val backgroundColor: LiveData<Int> = _backgroundColor.asLiveData()
    val collapseIconRotation: LiveData<Float> = _category.map { if (it.collapsed) -90f else 0f }.asLiveData()
    val isMoveDownPossible: LiveData<Boolean> = repository.isLastCategory(categoryId).map { !it }.asLiveData()
    val isMoveUpPossible: LiveData<Boolean> = repository.isFirstCategory(categoryId).map { !it }.asLiveData()
    val isSoundListVisible: LiveData<Boolean> = _category.map { !it.collapsed }.asLiveData()
    val name: LiveData<String> = _category.map { it.name }.asLiveData()
    val soundIds: LiveData<List<Int>> = soundRepository.listIdsByCategoryIdFiltered(categoryId).asLiveData()
    val spanCount: LiveData<Int> = settingsRepository.spanCount.asLiveData()
    @ColorInt
    val textColor: LiveData<Int> = _backgroundColor.map { colorHelper.getColorOnBackground(it) }.asLiveData()

    fun toggleCollapsed() = viewModelScope.launch { repository.toggleCollapsed(categoryId) }

    /** Switch places of this category and the next one, if any. */
    fun moveDown() = viewModelScope.launch {
        val categories = repository.categories.stateIn(viewModelScope).value
        val idx = categories.indexOfFirst { it.id == categoryId }
        // Double check so it's not already the last category:
        if (idx < categories.size - 1) {
            repository.update(
                categories[idx].clone(position = categories[idx].position + 1),
                categories[idx + 1].clone(position = categories[idx + 1].position - 1),
            )
        }
    }

    /** Switch places of this category and the previous one, if any. */
    fun moveUp() = viewModelScope.launch {
        val categories = repository.categories.stateIn(viewModelScope).value
        val idx = categories.indexOfFirst { it.id == categoryId }
        // Double check so it's not already the first category:
        if (idx > 0) {
            repository.update(
                categories[idx].clone(position = categories[idx].position - 1),
                categories[idx - 1].clone(position = categories[idx - 1].position + 1),
            )
        }
    }

    class Factory(
        private val repository: CategoryRepository,
        private val soundRepository: SoundRepository,
        private val settingsRepository: SettingsRepository,
        private val colorHelper: ColorHelper,
        private val categoryId: Int
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            if (modelClass.isAssignableFrom(CategoryViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return CategoryViewModel(repository, soundRepository, settingsRepository, colorHelper, categoryId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}