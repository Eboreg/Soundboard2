package us.huseli.soundboard2.viewmodels

import androidx.lifecycle.*
import kotlinx.coroutines.flow.map
import us.huseli.soundboard2.data.repositories.SoundRepository
import us.huseli.soundboard2.helpers.ColorHelper

class SoundViewModel(
    repository: SoundRepository,
    colorHelper: ColorHelper,
    soundId: Int
) : ViewModel() {
    private val _sound = repository.getSound(soundId)

    val backgroundColor: LiveData<Int?> = _sound.map { it?.backgroundColor }.asLiveData()
    val name: LiveData<String?> = _sound.map { it?.name }.asLiveData()
    val textColor: LiveData<Int?> = backgroundColor.map { it?.let { colorHelper.getColorOnBackground(it) } }
    val volume: LiveData<Int?> = _sound.map { it?.volume }.asLiveData()
    val progressTintColor: LiveData<Int?> = backgroundColor.map { it?.let { colorHelper.darkenOrBrighten(it) } }


    class Factory(private val repository: SoundRepository, private val colorHelper: ColorHelper, private val soundId: Int) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SoundViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SoundViewModel(repository, colorHelper, soundId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}