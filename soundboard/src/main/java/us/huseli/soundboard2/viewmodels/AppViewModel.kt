package us.huseli.soundboard2.viewmodels

import android.app.Application
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import us.huseli.soundboard2.Enums
import us.huseli.soundboard2.Functions
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.data.repositories.SettingsRepository
import us.huseli.soundboard2.data.repositories.SoundRepository
import us.huseli.soundboard2.helpers.LoggingObject
import java.util.*
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val soundRepository: SoundRepository,
    application: Application
) : LoggingObject, AndroidViewModel(application) {
    val categoryIds: LiveData<List<Int>> = categoryRepository.categoryIds.asLiveData()
    val spanCount: LiveData<Int> = settingsRepository.spanCount.asLiveData()
    val isZoomInPossible: LiveData<Boolean> = settingsRepository.isZoomInPossible.asLiveData()
    val repressMode: LiveData<Enums.RepressMode> = settingsRepository.repressMode.asLiveData()
    val selectEnabled: LiveData<Boolean> = settingsRepository.selectEnabled.asLiveData()
    val watchFolderEnabled: LiveData<Boolean> = settingsRepository.watchFolderEnabled.asLiveData()

    fun createDefaultCategory() = viewModelScope.launch { categoryRepository.createDefault() }

    fun setRepressMode(value: Enums.RepressMode) = settingsRepository.setRepressMode(value)

    fun setFilterTerm(value: String) {
        categoryRepository.filterTerm.value = value
    }

    fun zoomIn() = settingsRepository.zoomIn()

    fun zoomOut() = settingsRepository.zoomOut()

    /**
     * @param onChanges Callback that will be called if, and only if, any sounds are added or deleted.
     */
    fun syncWatchFolder(onChanges: ((added: Int, deleted: Int) -> Unit)? = null) = viewModelScope.launch {
        val treeUri = settingsRepository.watchFolderUri.value
        val context = getApplication<Application>().applicationContext
        var added = 0
        var deleted = 0

        if (treeUri != null) {
            // TODO: Does this work when there are no categories?
            val categoryId =
                settingsRepository.watchFolderCategoryId.value ?:
                categoryRepository.firstCategory.stateIn(viewModelScope).value.id
            val trashMissing = settingsRepository.watchFolderTrashMissing.value
            val fileUris = DocumentFile.fromTreeUri(context, treeUri)
                ?.listFiles()
                ?.filter { it.type?.startsWith("audio/") == true }
                ?.sortedBy { it.name?.lowercase(Locale.getDefault()) }
                ?.map { it.uri }

            // Get checksums and metadata in separate steps, to avoid wasting
            // CPU time on sounds we already imported before.
            val urisAndChecksums = fileUris?.map { Pair(it, Functions.extractChecksum(context, it)) } ?: emptyList()

            urisAndChecksums.forEach {
                if (!soundRepository.allChecksums.stateIn(viewModelScope).value.contains(it.second)) {
                    val soundFile = Functions.extractMetadata(context, it.first, it.second)
                    soundRepository.create(soundFile, categoryId)
                    added++
                }
            }

            if (trashMissing) {
                // If trashMissing == true, it means that the sounds we got from the watched folder are the ONLY
                // sounds there should be.
                soundRepository.sounds.stateIn(viewModelScope).value
                    .filterNot { sound -> sound.checksum in urisAndChecksums.map { it.second } }
                    .forEach {
                        soundRepository.delete(it)
                        deleted++
                    }
            }

            if (added > 0 || deleted > 0) onChanges?.invoke(added, deleted)
        }
    }
}