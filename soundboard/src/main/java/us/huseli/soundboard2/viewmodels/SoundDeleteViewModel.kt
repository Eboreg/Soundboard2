package us.huseli.soundboard2.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import us.huseli.soundboard2.data.repositories.SoundRepository
import us.huseli.soundboard2.data.repositories.StateRepository
import javax.inject.Inject

@HiltViewModel
class SoundDeleteViewModel @Inject constructor(
    private val repository: SoundRepository,
    private val stateRepository: StateRepository
) : ViewModel() {
    data class SoundDeleteData(val count: Int, val name: String)

    val soundData: LiveData<SoundDeleteData> = repository.selectedSounds.map {
        SoundDeleteData(it.size, if (it.size == 1) it[0].name else "")
    }.asLiveData()

    fun delete() = viewModelScope.launch {
        repository.delete(repository.selectedSounds.stateIn(viewModelScope).value)
        repository.disableSelect()
        stateRepository.push()
    }
}