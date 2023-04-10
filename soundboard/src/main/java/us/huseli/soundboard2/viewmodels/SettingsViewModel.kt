package us.huseli.soundboard2.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import us.huseli.soundboard2.R
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.data.repositories.SettingsRepository
import us.huseli.soundboard2.helpers.LoggingObject
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsRepository: SettingsRepository,
    private val categoryRepository: CategoryRepository
) : LoggingObject, ViewModel() {
    private val _watchFolderUri = MutableStateFlow(settingsRepository.watchFolderUri)

    val categories: LiveData<List<Category>> = categoryRepository.categories.asLiveData()
    val isAnimationEnabled = MutableStateFlow(settingsRepository.isAnimationEnabled)
    val isWatchFolderEnabled = MutableStateFlow(settingsRepository.isWatchFolderEnabled)
    val isWatchFolderEnabledLive = isWatchFolderEnabled.asLiveData()
    val watchFolderString: LiveData<String> =
        _watchFolderUri.map { it?.path?.split(":")?.last() ?: context.getString(R.string.not_set) }.asLiveData()
    val watchFolderTrashMissing = MutableStateFlow(settingsRepository.watchFolderTrashMissing)
    val watchFolderUri: LiveData<Uri?> = _watchFolderUri.asLiveData()
    val watchFolderCategoryPosition = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            settingsRepository.watchFolderCategory.stateIn(this).value?.let { category ->
                val categories = categoryRepository.categories.stateIn(this).value
                val categoryPosition = categories.indexOfFirst { it == category }
                if (categoryPosition > -1) watchFolderCategoryPosition.value = categoryPosition
            }
        }
    }

    fun save() = viewModelScope.launch {
        val categories = categoryRepository.categories.stateIn(this).value
        val watchFolderCategory = categories.getOrNull(watchFolderCategoryPosition.value)

        settingsRepository.setAnimationEnabled(isAnimationEnabled.value)
        settingsRepository.setWatchFolder(
            isWatchFolderEnabled.value,
            _watchFolderUri.value,
            watchFolderCategory?.id,
            watchFolderTrashMissing.value
        )
    }

    fun setWatchFolderUri(value: Uri?) {
        _watchFolderUri.value = value
    }
}