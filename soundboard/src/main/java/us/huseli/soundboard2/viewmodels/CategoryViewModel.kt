package us.huseli.soundboard2.viewmodels

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
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
    private val categoryId: Int
) : ViewModel() {
    private val _category = repository.get(categoryId).filterNotNull()
    private val _moveButtonsVisible = MutableStateFlow(false)

    val soundIds: LiveData<List<Int>> = soundRepository.listIdsByCategoryIdFiltered(categoryId).asLiveData()
    val backgroundColor: LiveData<Int> = _category.map { it.backgroundColor }.asLiveData()
    val textColor: LiveData<Int> = backgroundColor.map { colorHelper.getColorOnBackground(it) }
    val name: LiveData<String?> = _category.map { it.name }.asLiveData()
    val collapseIconRotation: LiveData<Float> = _category.map { if (it.collapsed) -90f else 0f }.asLiveData()
    val soundListVisible: LiveData<Boolean> = _category.map { !it.collapsed }.asLiveData()
    val spanCount: LiveData<Int> = settingsRepository.spanCount.asLiveData()
    val moveButtonsVisible: LiveData<Boolean> = _moveButtonsVisible.asLiveData()

    fun toggleCollapsed() = viewModelScope.launch { repository.toggleCollapsed(categoryId) }

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