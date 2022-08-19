package us.huseli.soundboard2.viewmodels

import android.net.Uri
import androidx.lifecycle.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
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
    settingsRepository: SettingsRepository,
    colorHelper: ColorHelper,
    soundId: Int
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
    val duration: LiveData<Long?> = _sound.map { it?.duration }.asLiveData()
    val durationString = _sound.map { sound ->
        sound?.duration?.let {
            when {
                it > -1 && it < 950 -> _decimalFormat.format(it.toDouble() / 1000) + "s"
                it > -1 -> (it.toDouble() / 1000).roundToInt().toString() + "s"
                else -> null
            }
        }
    }.asLiveData()

    private val _currentPositionPercent = flow<Int?> {
        var lastValue: Int? = null
        while (true) {
            val pos = _player.getCurrentPositionPercent()
            if (pos != null && pos != lastValue) {
                emit(pos)
                lastValue = pos
            }
            delay(100)
        }
    }

    @OptIn(FlowPreview::class)
    val soundProgress: LiveData<Int?> = _player.state.map { state ->
        when (state) {
            PlayState.STARTED -> _currentPositionPercent
            PlayState.PAUSED -> flowOf(_player.getCurrentPositionPercent())
            else -> _sound.map { it?.volume }
        }
    }.flattenMerge().asLiveData()

    fun play(path: String?, volume: Int, allowParallel: Boolean = false) = viewModelScope.launch {
        _player.start(path, volume.toFloat() / 100, allowParallel)
    }

    fun stop() = viewModelScope.launch { _player.stop() }

    fun stopPaused() = viewModelScope.launch { _player.stop(onlyPaused = true) }

    fun restart(path: String?, volume: Int) = viewModelScope.launch { _player.restart(path, volume.toFloat() / 100) }

    fun pause() = viewModelScope.launch { _player.pause() }


    class Factory(private val repository: SoundRepository, private val settingsRepository: SettingsRepository, private val colorHelper: ColorHelper, private val soundId: Int) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SoundViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SoundViewModel(repository, settingsRepository, colorHelper, soundId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}