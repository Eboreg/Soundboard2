package us.huseli.soundboard2.viewmodels

import android.net.Uri
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import us.huseli.soundboard2.Enums.PlayState
import us.huseli.soundboard2.Enums.RepressMode
import us.huseli.soundboard2.data.entities.SoundExtended
import us.huseli.soundboard2.data.repositories.SettingsRepository
import us.huseli.soundboard2.data.repositories.SoundRepository
import us.huseli.soundboard2.helpers.ColorHelper
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.helpers.SoundPlayer
import java.text.DecimalFormat
import kotlin.math.roundToInt

class SoundViewModel(
    repository: SoundRepository,
    private val settingsRepository: SettingsRepository,
    colorHelper: ColorHelper,
    private val soundId: Int
) : LoggingObject, ViewModel() {
    private val _player = SoundPlayer()
    private val _sound: Flow<SoundExtended?> = repository.getSound(soundId)
    private val _decimalFormat = DecimalFormat(".#").also {
        val symbols = it.decimalFormatSymbols
        symbols.decimalSeparator = '.'
        it.decimalFormatSymbols = symbols
    }

    val backgroundColor: LiveData<Int?> = _sound.map { it?.backgroundColor }.asLiveData()
    val name: LiveData<String?> = _sound.map { it?.name }.asLiveData()
    val textColor: LiveData<Int?> = backgroundColor.map { it?.let { colorHelper.getColorOnBackground(it) } }
    val volume: LiveData<Int?> = _sound.map { it?.volume }.asLiveData()
    val secondaryBackgroundColor: LiveData<Int?> = backgroundColor.map { it?.let { colorHelper.darkenOrBrighten(it, 0.3f, 0.3f) } }
    val playerError: LiveData<String?> = _player.error.asLiveData()
    val playState: LiveData<PlayState> = _player.state.asLiveData()
    val repressMode: LiveData<RepressMode> = settingsRepository.repressMode.asLiveData()
    val uri: LiveData<Uri?> = _sound.map { it?.uri }.asLiveData()
    val durationCardVisible: LiveData<Boolean> = _sound.map { it?.duration != null }.asLiveData()
    val animationsEnabled: LiveData<Boolean> = settingsRepository.animationsEnabled.asLiveData()

    val soundProgress = combine(
        settingsRepository.animationsEnabled,
        _sound.map { it?.volume },
        _player.currentPosition
    ) { animationsEnabled, volume, position ->
        if (animationsEnabled && position != null) position
        else volume
    }.asLiveData()

    val durationString = _sound.map { sound ->
        sound?.duration?.let {
            when {
                it > -1 && it < 950 -> _decimalFormat.format(it.toDouble() / 1000) + "s"
                it > -1 -> (it.toDouble() / 1000).roundToInt().toString() + "s"
                else -> null
            }
        }
    }.asLiveData()

    /** State booleans etc. */
    val isSelectEnabled: LiveData<Boolean> = settingsRepository.isSelectEnabled.asLiveData()
    val isSelected: LiveData<Boolean> = settingsRepository.selectedSounds.map { it.contains(soundId) }.asLiveData()
    val isPlayStateStarted: LiveData<Boolean> = _player.state.map { it == PlayState.STARTED }.asLiveData()
    val isPlayStatePaused: LiveData<Boolean> = _player.state.map { it == PlayState.PAUSED }.asLiveData()
    val isPlayStateError: LiveData<Boolean> = _player.state.map { it == PlayState.ERROR }.asLiveData()

    init {
        viewModelScope.launch {
            _sound.stateIn(viewModelScope).filterNotNull().collect {
                _player.setPath(it.uri.path)
                _player.setVolume(it.volume.toFloat())
            }
        }
    }

    fun play(allowParallel: Boolean = false) = viewModelScope.launch(Dispatchers.IO) { _player.start(allowParallel) }

    fun stop() = _player.stop()

    fun stopPaused() = _player.stop(onlyPaused = true)

    fun restart() = viewModelScope.launch(Dispatchers.IO) { _player.restart() }

    fun pause() = _player.pause()

    fun enableSelect() = settingsRepository.enableSelect()

    fun select() = settingsRepository.selectSound(soundId)

    fun unselect() = settingsRepository.unselectSound(soundId)

    override fun onCleared() {
        _player.release()
    }


    class Factory(private val repository: SoundRepository, private val settingsRepository: SettingsRepository, private val colorHelper: ColorHelper, private val soundId: Int) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            if (modelClass.isAssignableFrom(SoundViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SoundViewModel(repository, settingsRepository, colorHelper, soundId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}