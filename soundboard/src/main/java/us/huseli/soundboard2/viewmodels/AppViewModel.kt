package us.huseli.soundboard2.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import us.huseli.soundboard2.Enums
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.data.repositories.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val categoryIds = categoryRepository.categoryIds.asLiveData()
    val spanCount = settingsRepository.spanCount.asLiveData()
    val zoomInPossible = settingsRepository.zoomInPossible.asLiveData()
    val repressMode = settingsRepository.repressMode.asLiveData()

    fun setRepressMode(value: Enums.RepressMode) = settingsRepository.setRepressMode(value)

    fun setFilterTerm(value: String) {
        categoryRepository.filterTerm.value = value
    }

    fun zoomIn() = settingsRepository.zoomIn()

    fun zoomOut() = settingsRepository.zoomOut()
}