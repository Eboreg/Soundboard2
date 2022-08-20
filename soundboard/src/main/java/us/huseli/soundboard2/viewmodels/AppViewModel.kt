package us.huseli.soundboard2.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import us.huseli.soundboard2.Enums
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.data.repositories.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val categoryIds: LiveData<List<Int>> = categoryRepository.categoryIds.asLiveData()
    val spanCount: LiveData<Int> = settingsRepository.spanCount.asLiveData()
    val isZoomInPossible: LiveData<Boolean> = settingsRepository.isZoomInPossible.asLiveData()
    val repressMode: LiveData<Enums.RepressMode> = settingsRepository.repressMode.asLiveData()
    val isSelectEnabled: LiveData<Boolean> = settingsRepository.isSelectEnabled.asLiveData()

    fun createDefaultCategory() = viewModelScope.launch { categoryRepository.createDefault() }

    fun setRepressMode(value: Enums.RepressMode) = settingsRepository.setRepressMode(value)

    fun setFilterTerm(value: String) {
        categoryRepository.filterTerm.value = value
    }

    fun zoomIn() = settingsRepository.zoomIn()

    fun zoomOut() = settingsRepository.zoomOut()
}