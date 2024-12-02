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
import kotlinx.coroutines.flow.*
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
    data class SyncWatchFolderResult(
        val added: MutableList<String> = mutableListOf(),
        val deleted: MutableList<String> = mutableListOf()
    ) {
        fun isEmpty(): Boolean = added.isEmpty() && deleted.isEmpty()
    }

    init {
        viewModelScope.launch { stateRepository.push() }
    }

    private val _orphanFilesDeletedSignal = MutableSharedFlow<Int>()
    private val _orphanSoundsDeletedSignal = MutableSharedFlow<Int>()
    private val _redoSignal = MutableSharedFlow<Boolean>()
    private val _syncWatchFolderResult = MutableSharedFlow<SyncWatchFolderResult>()
    private val _undoSignal = MutableSharedFlow<Boolean>()
    // private var observer: FileObserver? = null

    val categorySoundMultimap: LiveData<Map<CategoryExtended, List<SoundExtended>>> =
        soundRepository.categorySoundMultimapVisible.asLiveData()
    val isAnimationEnabled: Boolean
        get() = settingsRepository.isAnimationEnabled
    val isRedoPossible: LiveData<Boolean> = stateRepository.isRedoPossible.asLiveData()
    val isSelectEnabled: LiveData<Boolean> = soundRepository.isSelectEnabled.asLiveData()
    val isUndoPossible: LiveData<Boolean> = stateRepository.isUndoPossible.asLiveData()
    val isWatchFolderEnabled: Boolean
        get() = settingsRepository.isWatchFolderEnabled
    val isZoomInPossible: LiveData<Boolean> = settingsRepository.isZoomInPossible.asLiveData()
    val orphanFilesDeletedSignal: SharedFlow<Int> = _orphanFilesDeletedSignal
    val orphanSoundsDeletedSignal: SharedFlow<Int> = _orphanSoundsDeletedSignal
    val redoSignal: SharedFlow<Boolean> = _redoSignal
    val repressMode: LiveData<Enums.RepressMode> = settingsRepository.repressMode.asLiveData()
    val soundFilterTerm: LiveData<String> = settingsRepository.soundFilterTerm.asLiveData()
    val spanCount: LiveData<Int> = settingsRepository.spanCount.asLiveData()
    val syncWatchFolderResult: SharedFlow<SyncWatchFolderResult> = _syncWatchFolderResult
    val selectedAndTotalSoundCount: LiveData<Pair<Int, Int>> = combine(
        soundRepository.selectedSounds.map { it.size },
        soundRepository.visibleSounds.map { it.size },
    ) { selected, total -> Pair(selected, total) }.asLiveData()
    var themeResId = R.style.SoundboardTheme
    val undoSignal: SharedFlow<Boolean> = _undoSignal
    val watchFolderCategory = settingsRepository.watchFolderCategory.asLiveData()
    val watchFolderTrashMissing: Boolean
        get() = settingsRepository.watchFolderTrashMissing

    fun createDefaultCategoryIfNeeded() = viewModelScope.launch(Dispatchers.IO) {
        if (categoryRepository.list().isEmpty()) categoryRepository.createDefault()
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

        if (deleted > 0) _orphanFilesDeletedSignal.emit(deleted)
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
        _orphanSoundsDeletedSignal.emit(deleted)
        stateRepository.replaceCurrent()
    }

    fun disableSelect() = soundRepository.disableSelect()

    fun redo() = viewModelScope.launch(Dispatchers.IO) {
        if (stateRepository.redo()) _redoSignal.emit(true)
    }

    fun selectAllSounds() = viewModelScope.launch(Dispatchers.IO) {
        soundRepository.visibleSounds.stateIn(this).value.forEach { soundRepository.select(it) }
    }

    fun setRepressMode(value: Enums.RepressMode) = settingsRepository.setRepressMode(value)

    fun setSoundFilterTerm(value: String) = settingsRepository.setSoundFilterTerm(value)

    fun stopAllSounds() = viewModelScope.launch {
        soundRepository.stopAll()
    }

    @Suppress("unused")
    fun syncWatchFolder() = syncWatchFolder(null)

    fun syncWatchFolder(onFinished: (() -> Unit)?) {
        viewModelScope.launch(Dispatchers.IO) {
            val treeUri = settingsRepository.watchFolderUri
            val context = getApplication<Application>().applicationContext

            if (treeUri != null) {
                val result = SyncWatchFolderResult()

                try {
                    // TODO: Does this work when there are no categories?
                    val category =
                        settingsRepository.watchFolderCategory.stateIn(this).value
                        ?: categoryRepository.firstCategory.stateIn(this).value
                    val trashMissing = settingsRepository.watchFolderTrashMissing
                    val folder = DocumentFile.fromTreeUri(context, treeUri)
                    val fileUris = folder
                        ?.listFiles()
                        ?.filter { it.type?.startsWith("audio/") == true }
                        ?.sortedBy { it.name?.lowercase(Locale.getDefault()) }
                        ?.map { it.uri }

                    /*
                    // Watches for individual file additions and deletions in
                    // watched folder. Probably not going to use this, but
                    // keeping it here anyway as it was sort of a bitch to
                    // figure out.

                    if (VERSION.SDK_INT >= VERSION_CODES.Q) {
                        val cursor = folder?.uri
                            ?.let { MediaStore.getMediaUri(context, it) }
                            ?.let { context.contentResolver.query(it, null, null, null, null) }
                        val columnIdx = cursor?.getColumnIndex(MediaStore.Files.FileColumns.DATA) ?: -1

                        if (columnIdx > -1 && cursor?.moveToFirst() == true) {
                            val path = cursor.getString(columnIdx)
                            observer = object : FileObserver(File(path), CREATE + DELETE + MOVED_FROM + MOVED_TO) {
                                override fun onEvent(event: Int, path: String?) {
                                    // path = just filename, e.g. "I'm gay 1.flac"
                                    log("WatchFolderObserver.onEvent: $path, $event")
                                }
                            }
                        }
                    }
                    */

                    // Get checksums and metadata in separate steps, to avoid
                    // wasting CPU time on sounds we already imported before.
                    val urisAndChecksums = fileUris?.map {
                        Pair(it, Functions.extractChecksum(context, it))
                    } ?: emptyList()

                    urisAndChecksums.forEach {
                        if (!soundRepository.allChecksums.stateIn(this).value.contains(it.second)) {
                            val soundFile = Functions.extractMetadata(context, it.first, it.second)
                            soundRepository.create(soundFile, category)
                            result.added.add(soundFile.name)
                        }
                    }

                    if (trashMissing) {
                        // If trashMissing == true, it means that the sounds we got
                        // from the watched folder are the ONLY sounds there should
                        // be.
                        val sounds = soundRepository.allSounds.stateIn(this).value
                            .filterNot { sound -> sound.checksum in urisAndChecksums.map { it.second } }
                        soundRepository.delete(sounds)
                        result.deleted.addAll(sounds.map { it.name })
                    }

                    if (!result.isEmpty()) {
                        _syncWatchFolderResult.emit(result)
                        stateRepository.replaceCurrent()
                    }
                } catch (_: Exception) {
                } finally {
                    onFinished?.invoke()
                }
            }
        }
    }

    fun undo() = viewModelScope.launch(Dispatchers.IO) {
        if (stateRepository.undo()) _undoSignal.emit(true)
    }

    fun zoomIn() = settingsRepository.zoomIn()
    fun zoomOut() = settingsRepository.zoomOut()
}