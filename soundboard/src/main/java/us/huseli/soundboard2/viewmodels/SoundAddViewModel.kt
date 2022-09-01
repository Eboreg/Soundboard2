package us.huseli.soundboard2.viewmodels

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.R
import us.huseli.soundboard2.data.SoundFile
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.entities.Sound
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.data.repositories.SoundRepository
import us.huseli.soundboard2.helpers.LoggingObject
import javax.inject.Inject

@HiltViewModel
class SoundAddViewModel @Inject constructor(
    @ApplicationContext context: Context,
    categoryRepository: CategoryRepository,
    private val soundRepository: SoundRepository
) : ViewModel(), LoggingObject {
    data class SoundCounts(val add: Int, val skip: Int)
    data class DuplicateData(val count: Int, val name: String)

    private val _duplicateAdd = MutableStateFlow(false)
    private var _selectedCategoryPosition = 0
    private val _name = MutableStateFlow<CharSequence>("")
    private var _volume = Constants.DEFAULT_VOLUME
    private val _soundFiles = MutableStateFlow<List<SoundFile>>(emptyList())

    private val _multiple: Flow<Boolean> = _soundFiles.map { it.size > 1 }

    private val _computedName: Flow<String> = _soundFiles.map {
        if (it.size == 1) it[0].name
        else context.getString(R.string.multiple_sounds_selected, it.size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _duplicates: Flow<List<Sound>> = _soundFiles.flatMapLatest { files ->
        val checksums = files.map { it.checksum }
        soundRepository.listByChecksums(checksums)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _soundCounts: Flow<SoundCounts> = _duplicateAdd.flatMapLatest { add ->
        _soundFiles.combine(_duplicates) { files: List<SoundFile>, dupes: List<Sound> ->
            if (add) SoundCounts(files.size, 0)
            else SoundCounts(files.size - dupes.size, dupes.size)
        }
    }

    val categories: LiveData<List<Category>> = categoryRepository.categories.asLiveData()
    val duplicateCount: LiveData<Int?> = _duplicates.map { it.size }.asLiveData()
    val addSoundCount: LiveData<Int?> = _soundCounts.map { it.add }.asLiveData()
    val skipSoundCount: LiveData<Int?> = _soundCounts.map { it.skip }.asLiveData()
    val multiple: LiveData<Boolean> = _multiple.asLiveData()
    val nameIsEditable: LiveData<Boolean> = _multiple.map { !it }.asLiveData()

    val selectedCategoryPosition: Int
        get() = _selectedCategoryPosition

    val volume: Int
        get() = _volume

    val duplicateData = merge(
        _duplicates.map {
            when (it.size) {
                1 -> DuplicateData(1, it[0].name)
                else -> DuplicateData(it.size, "")
            }
        },
        flow { emit(DuplicateData(0, "")) }
    ).asLiveData()

    val hasDuplicates: LiveData<Boolean> = merge(
        _duplicates.map { it.isNotEmpty() },
        flow { emit(false) }
    ).asLiveData()

    val duplicateAdd: LiveData<Boolean> = _duplicateAdd.asLiveData()

    val name: LiveData<CharSequence> = merge(
        _computedName,
        _name.filter { it != "" }
    ).asLiveData()

    fun setVolume(value: Int) { _volume = value }
    fun setSoundFiles(value: List<SoundFile>) { _soundFiles.value = value }
    fun setName(value: CharSequence) { _name.value = value }
    fun setDuplicateAdd(value: Boolean) { _duplicateAdd.value = value }
    fun setSelectedCategoryPosition(value: Int) { _selectedCategoryPosition = value }

    fun reset() {
        _soundFiles.value = emptyList()
        _volume = Constants.DEFAULT_VOLUME
        _duplicateAdd.value = false
        _name.value = ""
    }

    fun save(name: String, volume: Int, category: Category) = viewModelScope.launch {
        _soundFiles.value.forEach { soundFile ->
            val duplicate = _duplicates.stateIn(viewModelScope).value.firstOrNull { it.checksum == soundFile.checksum }
            if (_duplicateAdd.value || duplicate == null) {
                log("save(): soundFile=$soundFile, duplicate=$duplicate, volume=$volume, category=$category")
                soundRepository.create(soundFile, if (!_multiple.stateIn(viewModelScope).value) name else null, volume, category.id, duplicate)
            }
        }
    }
}