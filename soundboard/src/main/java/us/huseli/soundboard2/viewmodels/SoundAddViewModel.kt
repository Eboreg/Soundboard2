package us.huseli.soundboard2.viewmodels

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.R
import us.huseli.soundboard2.data.SoundFile
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.data.repositories.SoundRepository
import us.huseli.soundboard2.data.repositories.StateRepository
import us.huseli.soundboard2.helpers.LoggingObject
import javax.inject.Inject

@HiltViewModel
class SoundAddViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val soundRepository: SoundRepository,
    private val stateRepository: StateRepository,
    application: Application
) : LoggingObject, BaseSoundEditViewModel(application) {
    data class DuplicateData(val count: Int, val name: String)

    override val isUpdate = false

    private val soundFilesInternal = MutableStateFlow<List<SoundFile>>(emptyList())

    override val addSoundCount =
        combine(addDuplicates, soundFilesInternal, duplicatesInternal) { add, files, dupes ->
            if (add) files.size else files.size - dupes.size
        }.asLiveData()

    override val skipSoundCount =
        combine(addDuplicates, duplicatesInternal) { add, dupes -> if (add) 0 else dupes.size }.asLiveData()

    val duplicateData: LiveData<DuplicateData> = merge(
        duplicatesInternal.map {
            when (it.size) {
                1 -> DuplicateData(1, it[0].name)
                else -> DuplicateData(it.size, "")
            }
        },
        flow { emit(DuplicateData(0, "")) }
    ).asLiveData()

    override fun initialize() {
        super.initialize()

        addDuplicates.value = false
        categoryPosition.value = 0
        keepVolume.value = false
        overrideBackgroundColor.value = false
        soundFilesInternal.value = emptyList()
        volume.value = Constants.DEFAULT_VOLUME

        viewModelScope.launch(Dispatchers.IO) {
            categoriesInternal.value = categoryRepository.categories.stateIn(viewModelScope).value
        }
    }

    fun setSoundFiles(soundFiles: List<SoundFile>) {
        val checksums = soundFiles.map { it.checksum }
        val context = getApplication<Application>().applicationContext

        isReadyInternal.value = false
        soundCountInternal.value = soundFiles.size
        soundFilesInternal.value = soundFiles
        name.value =
            if (soundFiles.size == 1) soundFiles[0].name
            else context.getString(R.string.multiple_sounds_selected, soundFiles.size)

        viewModelScope.launch(Dispatchers.IO) {
            duplicatesInternal.value =
                soundRepository.allSounds.stateIn(viewModelScope).value.filter { it.checksum in checksums }
            isReadyInternal.value = true
        }
    }

    override fun save() {
        validate()

        viewModelScope.launch(Dispatchers.IO) {
            var created = false
            val category = categoriesInternal.value[categoryPosition.value]

            soundFilesInternal.value.forEach { soundFile ->
                val duplicate = duplicatesInternal.value.firstOrNull { it.checksum == soundFile.checksum }
                if (addDuplicates.value || duplicate == null) {
                    soundRepository.create(
                        soundFile = soundFile,
                        explicitName = if (soundCountInternal.value == 1) name.value else null,
                        volume = volume.value,
                        backgroundColor = if (overrideBackgroundColor.value) backgroundColor.value else null,
                        category = category,
                        duplicate = duplicate
                    )
                    created = true
                }
            }
            if (created) stateRepository.push()
        }
    }
}