package us.huseli.soundboard2.viewmodels

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import us.huseli.soundboard2.Enums.RepressMode
import us.huseli.soundboard2.data.entities.SoundExtended
import us.huseli.soundboard2.data.repositories.SettingsRepository
import us.huseli.soundboard2.data.repositories.SoundRepository
import us.huseli.soundboard2.helpers.ColorHelper
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.helpers.SoundPlayer
import java.lang.Integer.min
import java.text.DecimalFormat
import kotlin.math.max
import kotlin.math.roundToInt

class SoundViewModel(
    private val repository: SoundRepository,
    settingsRepository: SettingsRepository,
    colorHelper: ColorHelper,
    private val soundId: Int
) : LoggingObject, ViewModel() {
    private val _sound: Flow<SoundExtended> = repository.get(soundId).filterNotNull()
    private val _player = SoundPlayer()
    private val _decimalFormat = DecimalFormat(".#").also {
        val symbols = it.decimalFormatSymbols
        symbols.decimalSeparator = '.'
        it.decimalFormatSymbols = symbols
    }

    val backgroundColor: LiveData<Int> = _sound.map { it.backgroundColor }.asLiveData()
    val name: LiveData<String> = _sound.map { it.name }.asLiveData()
    val textColor: LiveData<Int> = backgroundColor.map { colorHelper.getColorOnBackground(it) }
    val volume: LiveData<Int> = _sound.map { it.volume }.asLiveData()
    val secondaryBackgroundColor: LiveData<Int> = backgroundColor.map { colorHelper.darkenOrBrighten(it) }
    val playerPermanentError: LiveData<String> = _player.permanentError.asLiveData()
    val playerTemporaryError: LiveData<String> = _player.temporaryError.asLiveData()
    val playerState: LiveData<SoundPlayer.State> = _player.state.asLiveData()
    val repressMode: LiveData<RepressMode> = settingsRepository.repressMode.asLiveData()
    val path: LiveData<String> = _sound.map { it.uri.path }.filterNotNull().asLiveData()
    val animationsEnabled: LiveData<Boolean> = settingsRepository.animationsEnabled.asLiveData()

    val soundProgress = combine(
        settingsRepository.animationsEnabled,
        _sound.map { it.volume },
        _player.currentPosition
    ) { animationsEnabled, volume, position ->
        if (animationsEnabled && position != null) position
        else volume
    }.asLiveData()

    val durationString = _sound.map { sound ->
        when {
            sound.duration > -1 && sound.duration < 950 -> _decimalFormat.format(sound.duration.toDouble() / 1000) + "s"
            sound.duration > -1 -> (sound.duration.toDouble() / 1000).roundToInt().toString() + "s"
            else -> null
        }
    }.asLiveData()

    /** State booleans etc. */
    val selectEnabled: LiveData<Boolean> = repository.selectEnabled.asLiveData()
    val selected: LiveData<Boolean> = repository.selectedSoundIds.map { it.contains(soundId) }.asLiveData()
    val playerStateStarted: LiveData<Boolean> = _player.state.map { it == SoundPlayer.State.STARTED }.asLiveData()
    val playerStatePaused: LiveData<Boolean> = _player.state.map { it == SoundPlayer.State.PAUSED }.asLiveData()
    val playerStateError: LiveData<Boolean> = _player.hasPermanentError.asLiveData()

    fun destroyParallelPlayers() = _player.destroyParallelPlayers()
    fun pause() = viewModelScope.launch(Dispatchers.IO) { _player.pause() }
    fun play() = viewModelScope.launch(Dispatchers.IO) { _player.play() }
    fun playParallel() = viewModelScope.launch(Dispatchers.IO) { _player.playParallel() }
    fun restart() = viewModelScope.launch(Dispatchers.IO) { _player.restart() }
    fun setPlayerPath(path: String) = _player.setPath(path)
    fun setPlayerVolume(volume: Int) = _player.setVolume(volume)
    fun stop() = _player.stop()
    fun stopPaused() = _player.stopPaused()

    fun enableSelect() = repository.enableSelect()
    fun select() = repository.select(soundId)
    fun unselect() = repository.unselect(soundId)

    fun selectAllFromLastSelected() = viewModelScope.launch {
        /** Select all sounds between this viewmodel's sound and the last selected one. */
        val lastSelectedId = repository.lastSelectedId.stateIn(viewModelScope).value
        log("selectAllFromLastSelected: lastSelected=$lastSelectedId")
        if (lastSelectedId != null) {
            val soundIds = repository.filteredSoundIdsOrdered.stateIn(viewModelScope).value
            val thisPos = soundIds.indexOf(soundId)
            val lastSelectedPos = soundIds.indexOf(lastSelectedId)
            log("selectAllFromLastSelected: thisPos=$thisPos, lastSelectedPos=$lastSelectedPos")
            if (thisPos > -1 && lastSelectedPos > -1) {
                soundIds.subList(min(thisPos, lastSelectedPos), max(thisPos, lastSelectedPos) + 1).forEach {
                    log("selectAllFromLastSelected: it=$it")
                    repository.select(it)
                }
            }
        }
    }

    override fun onCleared() = _player.destroy()


    class Factory(
        private val repository: SoundRepository,
        private val settingsRepository: SettingsRepository,
        private val colorHelper: ColorHelper,
        private val soundId: Int
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            if (modelClass.isAssignableFrom(SoundViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SoundViewModel(repository, settingsRepository, colorHelper, soundId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}