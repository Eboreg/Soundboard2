package us.huseli.soundboard2.viewmodels

import android.app.Application
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import us.huseli.soundboard2.Enums
import us.huseli.soundboard2.Functions
import us.huseli.soundboard2.R
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
    init {
        viewModelScope.launch { stateRepository.push() }
    }

    private val _snackbarText = Channel<CharSequence>()

    val categoryIds: LiveData<List<Int>> = categoryRepository.categoryIds.asLiveData()
    val isZoomInPossible: LiveData<Boolean> = settingsRepository.isZoomInPossible.asLiveData()
    val repressMode: LiveData<Enums.RepressMode> = settingsRepository.repressMode.asLiveData()
    val selectEnabled: LiveData<Boolean> = soundRepository.selectEnabled.asLiveData()
    val watchFolderEnabled: LiveData<Boolean> = settingsRepository.watchFolderEnabled.asLiveData()
    val watchFolderTrashMissing: LiveData<Boolean> = settingsRepository.watchFolderTrashMissing.asLiveData()
    val soundFilterTerm: LiveData<String> = settingsRepository.soundFilterTerm.asLiveData()
    val isUndoPossible: LiveData<Boolean> = stateRepository.isUndoPossible.asLiveData()
    val isRedoPossible: LiveData<Boolean> = stateRepository.isRedoPossible.asLiveData()

    // Using Flow instead of LiveData, because the latter does not seem to
    // enable us to display a message only _once_.
    val snackbarText: Flow<CharSequence> = _snackbarText.receiveAsFlow()

    private fun setSnackbarText(text: CharSequence) {
        _snackbarText.trySend(text)
    }

    private fun setSnackbarText(resId: Int) {
        val context = getApplication<Application>().applicationContext
        setSnackbarText(context.getText(resId))
    }

    fun createDefaultCategory() = viewModelScope.launch { categoryRepository.createDefault() }
    fun setRepressMode(value: Enums.RepressMode) = settingsRepository.setRepressMode(value)
    fun setSoundFilterTerm(value: String) { settingsRepository.setSoundFilterTerm(value) }
    fun zoomIn() = settingsRepository.zoomIn()
    fun zoomOut() = settingsRepository.zoomOut()

    fun selectAllSounds() = viewModelScope.launch {
        soundRepository.filteredSoundIdsOrdered.stateIn(this).value.forEach { soundRepository.select(it) }
    }

    fun unselectAllSounds() = viewModelScope.launch {
        soundRepository.allSoundIds.stateIn(this).value.forEach { soundId ->
            soundRepository.unselect(soundId)
        }
    }

    fun syncWatchFolder() = viewModelScope.launch {
        val treeUri = settingsRepository.watchFolderUri.value
        val context = getApplication<Application>().applicationContext
        var added = 0
        var deleted = 0

        if (treeUri != null) {
            // TODO: Does this work when there are no categories?
            val category =
                settingsRepository.watchFolderCategory.stateIn(this).value ?:
                categoryRepository.firstCategory.stateIn(this).value
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
                if (added > 0) snackbarString += context.resources.getQuantityString(R.plurals.watch_folder_sounds_added, added, added)
                if (deleted > 0) {
                    if (added > 0) snackbarString += ", "
                    snackbarString += context.resources.getQuantityString(R.plurals.watch_folder_sounds_deleted, deleted, deleted)
                }
                setSnackbarText(snackbarString)
                stateRepository.replaceCurrent()
            }
        }
    }

    fun undo() = viewModelScope.launch {
        if (stateRepository.undo()) setSnackbarText(R.string.undid)
    }

    fun redo() = viewModelScope.launch {
        if (stateRepository.redo()) setSnackbarText(R.string.redid)
    }

    fun deleteOrphans() = viewModelScope.launch {
        var deleted = 0
        val sounds = soundRepository.allSounds.stateIn(this).value
        sounds.forEach { sound ->
            sound.uri.path?.let {
                if (!File(it).isFile) {
                    soundRepository.delete(sound)
                    deleted++
                }
            }
        }
        if (deleted > 0) {
            val context = getApplication<Application>().applicationContext
            setSnackbarText(context.resources.getQuantityString(R.plurals.deleted_orphans, deleted, deleted))
            stateRepository.replaceCurrent()
        }
    }
}