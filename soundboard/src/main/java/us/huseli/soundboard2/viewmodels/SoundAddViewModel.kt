package us.huseli.soundboard2.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import us.huseli.soundboard2.BuildConfig
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.R
import us.huseli.soundboard2.data.SoundFile
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.entities.Sound
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.data.repositories.SoundRepository
import javax.inject.Inject

@HiltViewModel
class SoundAddViewModel @Inject constructor(
    @ApplicationContext context: Context,
    categoryRepository: CategoryRepository,
    private val soundRepository: SoundRepository
) : ViewModel() {
    data class SoundCounts(val add: Int?, val skip: Int?)

    private val _soundFiles = MutableStateFlow<List<SoundFile>>(emptyList())
    private val _duplicateAdd = MutableStateFlow(false)
    private val _name = MutableStateFlow("")
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
    var volume = Constants.DEFAULT_VOLUME
    val duplicateCount: LiveData<Int?> = _duplicates.map { it.size }.asLiveData()
    val multiple: LiveData<Boolean> = _multiple.asLiveData()
    val nameIsEditable: LiveData<Boolean> = _multiple.map { !it }.asLiveData()
    val addSoundCount = _soundCounts.map { it.add }.asLiveData()
    val skipSoundCount = _soundCounts.map { it.skip }.asLiveData()

    val hasDuplicates: LiveData<Boolean> = merge(
        _duplicates.map { it.isNotEmpty() },
        flow { emit(false) }
    ).asLiveData()

    val duplicateName: LiveData<String> = _duplicates.map {
        if (it.size == 1) it[0].name else ""
    }.asLiveData()

    val duplicateAdd: LiveData<Boolean>
        get() = _duplicateAdd.asLiveData()

    val name: LiveData<String> = merge(
        _computedName,
        _name.filter { it != "" }
    ).asLiveData()

    fun setSoundFiles(value: List<SoundFile>) {
        _soundFiles.value = value
    }

    fun setName(value: String) {
        _name.value = value
    }

    fun setDuplicateAdd(value: Boolean) {
        _duplicateAdd.value = value
    }

    fun reset() {
        _soundFiles.value = emptyList()
        volume = Constants.DEFAULT_VOLUME
        _duplicateAdd.value = false
        _name.value = ""
    }

    fun save(name: String, volume: Int, category: Category) = viewModelScope.launch {
        _soundFiles.value.forEach { soundFile ->
            val duplicate = _duplicates.stateIn(viewModelScope).value.firstOrNull { it.checksum == soundFile.checksum }
            if (_duplicateAdd.value || duplicate == null) {
                if (BuildConfig.DEBUG)
                    Log.d(LOG_TAG, "save(): soundFile=$soundFile, duplicate=$duplicate, volume=$volume, category=$category")
                soundRepository.create(soundFile, if (!_multiple.stateIn(viewModelScope).value) name else null, volume, category.id, duplicate)
            }
        }
    }

    companion object {
        const val LOG_TAG = "SoundAddViewModel"
    }
}