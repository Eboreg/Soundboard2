package us.huseli.soundboard2.viewmodels

import android.content.Context
import android.graphics.Color
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.R
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.data.repositories.SoundRepository
import javax.inject.Inject

@HiltViewModel
class SoundEditViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: SoundRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {
    private val _emptyCategory = Category(-1, context.getString(R.string.not_changed), Color.DKGRAY, -1)
    private var _keepVolume = true
    private val _name = MutableStateFlow<CharSequence>("")
    private val _volume = MutableStateFlow<Int?>(null)
    private val _categories: Flow<List<Category>> = categoryRepository.categories.map { listOf(_emptyCategory) + it }
    private val _category = MutableStateFlow(_emptyCategory)

    private val _originalName: Flow<String> = repository.selectedSounds.map {
        if (it.size == 1) it[0].name
        else context.getString(R.string.multiple_sounds_selected, it.size)
    }
    
    private val _originalVolume: Flow<Int> = repository.selectedSounds.map { sounds ->
        val volumes = sounds.map { it.volume }.toSet()
        if (volumes.size == 1) volumes.first()
        else Constants.DEFAULT_VOLUME
    }

    val categories: LiveData<List<Category>> = _categories.asLiveData()
    val nameIsEditable: LiveData<Boolean> = repository.selectedSoundIds.map { it.size == 1 }.asLiveData()
    val soundCount: LiveData<Int> = repository.selectedSoundIds.map { it.size }.asLiveData()
    val name: LiveData<CharSequence> = merge(_originalName, _name.filter { it != "" } ).asLiveData()
    val volume: LiveData<Int> = merge(_originalVolume, _volume.filterNotNull()).asLiveData()

    val categoryPosition: LiveData<Int> = combine(_categories, _category) { categories, category ->
        categories.indexOfFirst { it.id == category.id }
    }.filter { it >= 0 }.asLiveData()

    val keepVolume: Boolean
        get() = _keepVolume

    fun setName(value: CharSequence) { _name.value = value }
    fun setVolume(value: Int) { _volume.value = value }
    fun setKeepVolume(value: Boolean) { _keepVolume = value }
    fun setCategory(value: Category) { _category.value = value }

    fun save(name: String?, keepVolume: Boolean, volume: Int, category: Category) = viewModelScope.launch {
        val sounds = repository.selectedSounds.stateIn(viewModelScope).value
        repository.update(
            sounds,
            name,
            if (!keepVolume) volume else null,
            if (category.id != -1) category else null
        )
        repository.disableSelect()
    }

    fun reset() {
        _name.value = ""
        _volume.value = null
        _keepVolume = true
        _category.value = _emptyCategory
    }
}