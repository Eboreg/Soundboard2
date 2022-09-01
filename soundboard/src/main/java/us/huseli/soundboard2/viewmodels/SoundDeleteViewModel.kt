package us.huseli.soundboard2.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import us.huseli.soundboard2.data.repositories.SoundRepository
import javax.inject.Inject

@HiltViewModel
class SoundDeleteViewModel @Inject constructor(private val repository: SoundRepository) : ViewModel() {
    data class SoundDeleteData(val count: Int, val name: String)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _sounds = repository.selectedSoundIds.flatMapLatest { soundIds -> repository.listByIds(soundIds) }

    val soundData: LiveData<SoundDeleteData> = _sounds.map {
        SoundDeleteData(it.size, if (it.size == 1) it[0].name else "")
    }.asLiveData()

    fun delete() = viewModelScope.launch {
        repository.delete(_sounds.stateIn(viewModelScope).value)
        repository.disableSelect()
    }
}