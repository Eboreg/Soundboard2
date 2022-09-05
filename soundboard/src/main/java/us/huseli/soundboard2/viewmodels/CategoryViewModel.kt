package us.huseli.soundboard2.viewmodels

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.entities.SoundExtended
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.data.repositories.SettingsRepository
import us.huseli.soundboard2.data.repositories.SoundRepository
import us.huseli.soundboard2.helpers.ColorHelper

class CategoryViewModel(
    private val repository: CategoryRepository,
    soundRepository: SoundRepository,
    settingsRepository: SettingsRepository,
    colorHelper: ColorHelper,
    private val category: Category
) : ViewModel() {
    private val _categoryId = category.id
    private val _category = MutableStateFlow(category)
    private val _moveButtonsVisible = MutableStateFlow(false)

    // @OptIn(ExperimentalCoroutinesApi::class)
    // val soundIds: LiveData<List<Int>> =
    //     _category.filterNotNull().flatMapLatest { repository.listSoundIdsFiltered(it) }.asLiveData()
    // val _soundIds: LiveData<List<Int>> = repository.listSoundIdsFiltered(categoryId).asLiveData()
    // val sounds: LiveData<List<SoundExtended>> = soundRepository.allSounds.map { sounds -> sounds.filter { it.categoryId == _categoryId } }.asLiveData()
    val sounds: LiveData<List<SoundExtended>> = soundRepository.listByCategoryFiltered(category).asLiveData()
    val backgroundColor: LiveData<Int> = _category.map { it.backgroundColor }.asLiveData()
    val textColor: LiveData<Int> = _category.map { colorHelper.getColorOnBackground(it.backgroundColor) }.asLiveData()
    val name: LiveData<String?> = _category.map { it.name }.asLiveData()
    val collapseIconRotation: LiveData<Float> = _category.map { if (it.collapsed) -90f else 0f }.asLiveData()
    val soundListVisible: LiveData<Boolean> = _category.map { !it.collapsed }.asLiveData()
    val spanCount: LiveData<Int> = settingsRepository.spanCount.asLiveData()
    val moveButtonsVisible: LiveData<Boolean> = _moveButtonsVisible.asLiveData()

    fun toggleCollapsed() = viewModelScope.launch { repository.toggleCollapsed(category) }

    class Factory(
        private val repository: CategoryRepository,
        private val soundRepository: SoundRepository,
        private val settingsRepository: SettingsRepository,
        private val colorHelper: ColorHelper,
        private val category: Category
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            if (modelClass.isAssignableFrom(CategoryViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return CategoryViewModel(repository, soundRepository, settingsRepository, colorHelper, category) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}