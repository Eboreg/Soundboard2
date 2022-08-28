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
    private val _categories = categoryRepository.categories
    private val _newWatchFolderUri = MutableStateFlow<Uri?>(null)
    private val _watchFolderString =
        MutableStateFlow(settingsRepository.watchFolderUri.value?.path?.split(":")?.last())
    private val _watchFolderEnabled = MutableStateFlow(settingsRepository.watchFolderEnabled.value)
    private val _animationsEnabled = MutableStateFlow(settingsRepository.animationsEnabled.value)

    private val _watchFolderCategoryPosition = combine(
        settingsRepository.watchFolderCategoryId,
        _categories
    ) { categoryId, categories ->
        val result = categories.indexOfFirst { it.id == categoryId }
        log("_watchFolderCategoryPosition: categoryId=$categoryId, categories=$categories, result=$result")
        result
    }.filter { it != -1 }

    val animationsEnabled: LiveData<Boolean> = _animationsEnabled.asLiveData()
    val watchFolderEnabled: LiveData<Boolean> = _watchFolderEnabled.asLiveData()
    val watchFolderUri: LiveData<Uri?> = settingsRepository.watchFolderUri.asLiveData()
    val watchFolderTrashMissing: LiveData<Boolean> = settingsRepository.watchFolderTrashMissing.asLiveData()
    val watchFolderString: LiveData<String> =
        _watchFolderString.map { it ?: context.getString(R.string.not_set) }.asLiveData()
    val watchFolderCategoryPosition: LiveData<Int> = _watchFolderCategoryPosition.map { it }.asLiveData()
    val categories: LiveData<List<Category>> = _categories.asLiveData()

    fun setAnimationsEnabled(value: Boolean) {
        _animationsEnabled.value = value
    }

    fun setWatchFolderEnabled(value: Boolean) {
        _watchFolderEnabled.value = value
    }

    fun setWatchFolderUri(value: Uri?) {
        _newWatchFolderUri.value = value
        _watchFolderString.value = value?.path?.split(":")?.last()
    }

    fun save(
        animationsEnabled: Boolean,
        watchFolderEnabled: Boolean,
        watchFolderUri: Uri?,
        watchFolderCategory: Category?,
        watchFolderTrashMissing: Boolean
    ) = viewModelScope.launch {
        log("save(): animationsEnabled=$animationsEnabled, watchFolderEnabled=$watchFolderEnabled, watchFolderUri=$watchFolderUri, watchFolderCategory=$watchFolderCategory, watchFolderTrashMissing=$watchFolderTrashMissing")
        if (animationsEnabled)
            settingsRepository.enableAnimations()
        else
            settingsRepository.disableAnimations()
        settingsRepository.setWatchFolder(
            watchFolderEnabled,
            watchFolderUri,
            watchFolderCategory?.id,
            watchFolderTrashMissing
        )
    }
}