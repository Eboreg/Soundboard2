package us.huseli.soundboard2.viewmodels

import android.app.Application
import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.Enums
import us.huseli.soundboard2.Functions
import us.huseli.soundboard2.R
import us.huseli.soundboard2.data.entities.CategoryExtended
import us.huseli.soundboard2.data.entities.SoundExtended
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.data.repositories.SettingsRepository
import us.huseli.soundboard2.data.repositories.SoundRepository
import us.huseli.soundboard2.data.repositories.StateRepository
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.helpers.SnackbarTextListener
import java.io.File
import java.util.*
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val soundRepository: SoundRepository,
    private val stateRepository: StateRepository,
    application: Application
) : LoggingObject, AndroidViewModel(application) {
    init {
        viewModelScope.launch { stateRepository.push() }
    }

    private var _snackbarTextListener: SnackbarTextListener? = null

    val categorySoundMultimap: LiveData<Map<CategoryExtended, List<SoundExtended>>> =
        soundRepository.categorySoundMultimapVisible.asLiveData()
    val isRedoPossible: LiveData<Boolean> = stateRepository.isRedoPossible.asLiveData()
    val isSelectEnabled: LiveData<Boolean> = soundRepository.isSelectEnabled.asLiveData()
    val isUndoPossible: LiveData<Boolean> = stateRepository.isUndoPossible.asLiveData()
    val isWatchFolderEnabled: Boolean
        get() = settingsRepository.isWatchFolderEnabled
    val isZoomInPossible: LiveData<Boolean> = settingsRepository.isZoomInPossible.asLiveData()
    val repressMode: LiveData<Enums.RepressMode> = settingsRepository.repressMode.asLiveData()
    val soundFilterTerm: LiveData<String> = settingsRepository.soundFilterTerm.asLiveData()
    val spanCount: LiveData<Int> = settingsRepository.spanCount.asLiveData()
    val watchFolderTrashMissing: Boolean
        get() = settingsRepository.watchFolderTrashMissing
    val selectedAndTotalSoundCount: LiveData<Pair<Int, Int>> = combine(
        soundRepository.selectedSounds.map { it.size },
        soundRepository.visibleSounds.map { it.size },
    ) { selected, total -> Pair(selected, total) }.asLiveData()

    fun createDefaultCategoryIfNeeded() = viewModelScope.launch(Dispatchers.IO) {
        if (categoryRepository.list().isEmpty()) categoryRepository.createDefault()
    }

    fun setRepressMode(value: Enums.RepressMode) = settingsRepository.setRepressMode(value)
    fun setSoundFilterTerm(value: String) = settingsRepository.setSoundFilterTerm(value)

    fun zoomIn() = settingsRepository.zoomIn()
    fun zoomOut() = settingsRepository.zoomOut()

    fun setSnackbarTextListener(listener: SnackbarTextListener) {
        _snackbarTextListener = listener
    }

    fun disableSelect() = soundRepository.disableSelect()

    fun selectAllSounds() = viewModelScope.launch(Dispatchers.IO) {
        soundRepository.visibleSounds.stateIn(this).value.forEach { soundRepository.select(it) }
    }

    fun syncWatchFolder() = viewModelScope.launch(Dispatchers.IO) {
        val treeUri = settingsRepository.watchFolderUri
        val context = getApplication<Application>().applicationContext
        var added = 0
        var deleted = 0

        if (treeUri != null) {
            // TODO: Does this work when there are no categories?
            val category =
                settingsRepository.watchFolderCategory.stateIn(this).value
                ?: categoryRepository.firstCategory.stateIn(this).value
            val trashMissing = settingsRepository.watchFolderTrashMissing
            val fileUris = DocumentFile.fromTreeUri(context, treeUri)
                ?.listFiles()
                ?.filter { it.type?.startsWith("audio/") == true }
                ?.sortedBy { it.name?.lowercase(Locale.getDefault()) }
                ?.map { it.uri }

            // Get checksums and metadata in separate steps, to avoid wasting
            // CPU time on sounds we already imported before.
            val urisAndChecksums = fileUris?.map { Pair(it, Functions.extractChecksum(context, it)) } ?: emptyList()

            urisAndChecksums.forEach {
                if (!soundRepository.allChecksums.stateIn(this).value.contains(it.second)) {
                    val soundFile = Functions.extractMetadata(context, it.first, it.second)
                    soundRepository.create(soundFile, category)
                    added++
                }
            }

            if (trashMissing) {
                // If trashMissing == true, it means that the sounds we got from the watched folder are the ONLY
                // sounds there should be.
                val sounds = soundRepository.allSounds.stateIn(this).value
                    .filterNot { sound -> sound.checksum in urisAndChecksums.map { it.second } }
                soundRepository.delete(sounds)
                deleted = sounds.size
            }

            if (added > 0 || deleted > 0) {
                var snackbarString = context.getString(R.string.watched_folder_sync) + ": "
                if (added > 0) snackbarString += context.resources.getQuantityString(
                    R.plurals.watch_folder_sounds_added,
                    added,
                    added
                )
                if (deleted > 0) {
                    if (added > 0) snackbarString += ", "
                    snackbarString += context.resources.getQuantityString(
                        R.plurals.watch_folder_sounds_deleted,
                        deleted,
                        deleted
                    )
                }
                _snackbarTextListener?.setSnackbarText(snackbarString)
                stateRepository.replaceCurrent()
            }
        }
    }

    fun undo() = viewModelScope.launch(Dispatchers.IO) {
        if (stateRepository.undo()) _snackbarTextListener?.setSnackbarText(R.string.undid)
    }

    fun redo() = viewModelScope.launch(Dispatchers.IO) {
        if (stateRepository.redo()) _snackbarTextListener?.setSnackbarText(R.string.redid)
    }

    fun deleteOrphanSoundObjects() = viewModelScope.launch(Dispatchers.IO) {
        /** Checks for Sound objects with missing files, deletes the objects. */
        var deleted = 0
        val sounds = soundRepository.allSounds.stateIn(this).value
        sounds.forEach { sound ->
            sound.uri.path?.let {
                if (!File(it).isFile) {
                    soundRepository.delete(listOf(sound))
                    deleted++
                }
            }
        }
        _snackbarTextListener?.setSnackbarText(
            getApplication<Application>().applicationContext.resources.getQuantityString(
                R.plurals.deleted_orphan_sounds,
                deleted,
                deleted
            )
        )
        stateRepository.replaceCurrent()
    }

    fun deleteOrphanSoundFiles() = viewModelScope.launch(Dispatchers.IO) {
        /** Checks for files with missing Sound objects (including any undo states), deletes the files. */
        val context = getApplication<Application>().applicationContext
        val directory = context.getDir(Constants.SOUND_DIRNAME, Context.MODE_PRIVATE)
        val soundPaths = soundRepository.list().mapNotNull { it.uri.path }.toSet() + stateRepository.allPaths
        var deleted = 0

        log("deleteOrphanSoundFiles: soundPaths=$soundPaths")

        directory.listFiles { file -> file.isFile }?.forEach { file ->
            if (file.path !in soundPaths && file.delete()) {
                log("deleteOrphanSoundFiles: deleted ${file.path}")
                deleted++
            }
        }

        if (deleted > 0) _snackbarTextListener?.setSnackbarText(
            context.resources.getQuantityString(
                R.plurals.deleted_orphan_files,
                deleted,
                deleted
            )
        )
    }

    fun stopAllSounds() = viewModelScope.launch {
        soundRepository.stopAll()
    }
}