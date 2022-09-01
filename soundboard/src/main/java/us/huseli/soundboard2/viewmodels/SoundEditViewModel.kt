package us.huseli.soundboard2.viewmodels

import android.content.Context
import android.graphics.Color
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private var _selectedCategoryPosition = 0

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _sounds = repository.selectedSoundIds.flatMapLatest { soundIds -> repository.listByIds(soundIds) }
    private val _categories = categoryRepository.categories.map { listOf(_emptyCategory) + it }

    private val _name = MutableStateFlow<CharSequence>("")
    private val _volume = MutableStateFlow<Int?>(null)

    private val _originalName: Flow<String> = _sounds.map {
        if (it.size == 1) it[0].name
        else context.getString(R.string.multiple_sounds_selected, it.size)
    }
    
    private val _originalVolume: Flow<Int> = _sounds.map { sounds ->
        val volumes = sounds.map { it.volume }.toSet()
        if (volumes.size == 1) volumes.first()
        else Constants.DEFAULT_VOLUME
    }

    val categories = _categories.asLiveData()
    val nameIsEditable: LiveData<Boolean> = _sounds.map { it.size == 1 }.asLiveData()
    val soundCount: LiveData<Int> = _sounds.map { it.size }.asLiveData()

    val name: LiveData<CharSequence> = merge(_originalName, _name.filter { it != "" } ).asLiveData()
    val volume: LiveData<Int> = merge(_originalVolume, _volume.filterNotNull()).asLiveData()

    val selectedCategoryPosition: Int
        get() = _selectedCategoryPosition

    val keepVolume: Boolean
        get() = _keepVolume

    fun setName(value: CharSequence) { _name.value = value }
    fun setVolume(value: Int) { _volume.value = value }
    fun setKeepVolume(value: Boolean) { _keepVolume = value }
    fun setCategoryPosition(value: Int) { _selectedCategoryPosition = value }

    fun save(name: String?, keepVolume: Boolean, volume: Int, category: Category) = viewModelScope.launch {
        val sounds = _sounds.stateIn(viewModelScope).value
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
        _selectedCategoryPosition = 0
    }
}