package us.huseli.soundboard2.viewmodels

import android.content.Context
import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
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
    private val _noCategory = Category(-1, context.getString(R.string.not_set), Color.DKGRAY, -1)
    private val _categories = categoryRepository.categories.map { listOf(_noCategory) + it }

    private val _watchFolderCategoryPosition = MutableStateFlow(0).apply {
        viewModelScope.launch {
            val idFlow = combine(settingsRepository.watchFolderCategoryId, _categories) { categoryId, categories ->
                categories.indexOfFirst { it.id == categoryId }
            }.filter { it != -1 }
            emitAll(idFlow)
        }
    }
    private val _watchFolder = MutableStateFlow<String?>(null).apply {
        viewModelScope.launch { emitAll(settingsRepository.watchFolder) }
    }
    private val _animationsEnabled = MutableStateFlow<Boolean?>(null).apply {
        viewModelScope.launch { emitAll(settingsRepository.animationsEnabled) }
    }

    val animationsEnabled = _animationsEnabled.filterNotNull().map {
        log("animationsEnabled=$it")
        it
    }.asLiveData()
    val watchFolderString = _watchFolder.map {
        log("watchFolderString=$it")
        it ?: context.getString(R.string.not_set)
    }.asLiveData()
    val watchFolderCategoryPosition = _watchFolderCategoryPosition.map {
        log("watchFolderCategoryPosition=$it")
        it
    }.asLiveData()
    val watchFolderCategoryName = combine(_watchFolderCategoryPosition, _categories) { position, categories ->
        categories[position].name
    }.asLiveData()
    val categories = _categories.asLiveData()

    fun setAnimationsEnabled(value: Boolean) {
        _animationsEnabled.value = value
    }

    fun setWatchFolderCategoryPosition(value: Int) {
        _watchFolderCategoryPosition.value = value
    }

    fun setWatchFolder(value: String?) {
        _watchFolder.value = value
    }

    fun save() = viewModelScope.launch {
        val category = _categories.stateIn(viewModelScope).value[_watchFolderCategoryPosition.value]
        log("save(): category=$category, _animationsEnabled=${_animationsEnabled.value}, _watchFolder=${_watchFolder.value}")
        when (_animationsEnabled.value) {
            true -> settingsRepository.enableAnimations()
            false -> settingsRepository.disableAnimations()
            else -> {}
        }
        settingsRepository.setWatchFolder(_watchFolder.value)
        settingsRepository.setWatchFolderCategoryId(category.id)
    }
}