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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
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
    categoryRepository: CategoryRepository
) : LoggingObject, ViewModel() {
    private val _isAnimationEnabled = MutableStateFlow(settingsRepository.isAnimationEnabled.value)
    private val _isWatchFolderEnabled = MutableStateFlow(settingsRepository.isWatchFolderEnabled.value)
    private val _watchFolderUri = MutableStateFlow(settingsRepository.watchFolderUri.value)

    val categories: LiveData<List<Category>> = categoryRepository.categories.asLiveData()
    val isAnimationEnabled: LiveData<Boolean> = _isAnimationEnabled.asLiveData()
    val isWatchFolderEnabled: LiveData<Boolean> = _isWatchFolderEnabled.asLiveData()
    val watchFolderString: LiveData<String> =
        _watchFolderUri.map { it?.path?.split(":")?.last() ?: context.getString(R.string.not_set) }.asLiveData()
    val watchFolderTrashMissing: LiveData<Boolean> = settingsRepository.watchFolderTrashMissing.asLiveData()
    val watchFolderUri: LiveData<Uri?> = settingsRepository.watchFolderUri.asLiveData()

    val watchFolderCategoryPosition: LiveData<Int> = combine(
        settingsRepository.watchFolderCategory,
        categoryRepository.categories
    ) { categoryId, categoryIds ->
        categoryIds.indexOfFirst { it == categoryId }
    }.filter { it != -1 }.asLiveData()

    fun setAnimationEnabled(value: Boolean) {
        _isAnimationEnabled.value = value
    }

    fun setWatchFolderEnabled(value: Boolean) {
        _isWatchFolderEnabled.value = value
    }

    fun setWatchFolderUri(value: Uri?) {
        _watchFolderUri.value = value
    }

    fun save(
        animationsEnabled: Boolean,
        watchFolderEnabled: Boolean,
        watchFolderUri: Uri?,
        watchFolderCategory: Category?,
        watchFolderTrashMissing: Boolean
    ) = viewModelScope.launch {
        log("save(): animationsEnabled=$animationsEnabled, watchFolderEnabled=$watchFolderEnabled, watchFolderUri=$watchFolderUri, watchFolderCategory=$watchFolderCategory, watchFolderTrashMissing=$watchFolderTrashMissing")
        if (animationsEnabled) settingsRepository.enableAnimations()
        else settingsRepository.disableAnimations()
        settingsRepository.setWatchFolder(
            watchFolderEnabled,
            watchFolderUri,
            watchFolderCategory?.id,
            watchFolderTrashMissing
        )
    }
}