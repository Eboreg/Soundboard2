package us.huseli.soundboard2.viewmodels

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import us.huseli.soundboard2.Enums.RepressMode
import us.huseli.soundboard2.data.entities.SoundExtended
import us.huseli.soundboard2.data.repositories.SettingsRepository
import us.huseli.soundboard2.data.repositories.SoundRepository
import us.huseli.soundboard2.helpers.ColorHelper
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.helpers.PlayerEventListener
import us.huseli.soundboard2.helpers.SoundPlayer
import java.lang.Integer.min
import java.text.DecimalFormat
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

@HiltViewModel
class SoundViewModel @Inject constructor(
    private val repository: SoundRepository,
    private val settingsRepository: SettingsRepository,
    colorHelper: ColorHelper,
) : LoggingObject, ViewModel(), PlayerEventListener {
    private val decimalFormatInternal = DecimalFormat(".#").also {
        val symbols = it.decimalFormatSymbols
        symbols.decimalSeparator = '.'
        it.decimalFormatSymbols = symbols
    }
    private val isPlayerErrorInternal = MutableStateFlow(false)
    private val isPlayerPausedInternal = MutableStateFlow(false)
    private val isPlayerStartedInternal = MutableStateFlow(false)
    private var playerEventListenerInternal: PlayerEventListener? = null
    private var soundPlayerInternal: SoundPlayer? = null
    private val soundIdInternal = MutableStateFlow<Int?>(null)

    private val soundInternal: Flow<SoundExtended> =
        combine(soundIdInternal, repository.allSounds) { soundId, sounds ->
            sounds.find { it.id == soundId }
        }.filterNotNull()

    private val isSelectedInternal = combine(soundInternal, repository.selectedSounds) { sound, selectedSounds ->
        selectedSounds.contains(sound)
    }

    @ColorInt
    val backgroundColor: LiveData<Int?> = soundInternal.map {
        if (it.backgroundColor == Color.TRANSPARENT) it.categoryColor else it.backgroundColor
    }.asLiveData()
    val durationString: LiveData<String?> = soundInternal.map { sound ->
        when {
            sound.duration > -1 && sound.duration < 950 -> decimalFormatInternal.format(sound.duration.toDouble() / 1000) + "s"
            sound.duration > -1 -> (sound.duration.toDouble() / 1000).roundToInt().toString() + "s"
            else -> null
        }
    }.asLiveData()
    val isAnimationEnabled: Boolean
        get() = settingsRepository.isAnimationEnabled
    val name: LiveData<String> = soundInternal.map { it.name }.asLiveData()
    val playerState: SoundPlayer.State?
        get() = soundPlayerInternal?.state
    val repressMode: LiveData<RepressMode> = settingsRepository.repressMode.asLiveData()
    /*
    val screenHeightPx: Int
        get() = settingsRepository.screenHeightPx
    val scrollEndSignal: LiveData<Boolean> = settingsRepository.scrollEndSignal.asLiveData()
     */
    val secondaryBackgroundColor: LiveData<Int?> = backgroundColor.map { color ->
        color?.let { colorHelper.darkenOrBrighten(it) }
    }
    @ColorInt
    val textColor: LiveData<Int?> = backgroundColor.map { color ->
        color?.let { colorHelper.getColorOnBackground(it) }
    }
    @IntRange(from = 0, to = 100)
    val volume: LiveData<Int> = soundInternal.map { it.volume }.asLiveData()

    /** State booleans etc: */

    val isPlayerError: LiveData<Boolean> = isPlayerErrorInternal.asLiveData()
    val isPlayerPaused: LiveData<Boolean> = isPlayerPausedInternal.asLiveData()
    val isPlayerStarted: LiveData<Boolean> = isPlayerStartedInternal.asLiveData()
    val isSelectEnabled: LiveData<Boolean> = repository.isSelectEnabled.asLiveData()
    val isSelected: LiveData<Boolean> = isSelectedInternal.asLiveData()

    init {
        /**
         * Has to be down here because: "During the initialization of an instance, the initializer blocks are executed
         * in the same order as they appear in the class body".
         */
        viewModelScope.launch(Dispatchers.Default) {
            soundPlayerInternal = SoundPlayer(this).also { it.setPlaybackEventListener(this@SoundViewModel) }

            launch {
                soundInternal.collect { sound ->
                    soundPlayerInternal?.setPath(sound.uri.path)
                    soundPlayerInternal?.setVolume(sound.volume)
                }
            }

            launch {
                repository.stopAllSignal.collect { soundPlayerInternal?.stopStartedOrPaused() }
            }
        }
    }

    fun setSoundId(soundId: Int) {
        log("ADAPTERDEBUG setSoundId: soundId=$soundId, soundIdInternal=${soundIdInternal.value}")
        if (soundId != soundIdInternal.value) {
            soundIdInternal.value = soundId
        }
    }

    fun setPlaybackEventListener(listener: PlayerEventListener) {
        playerEventListenerInternal = listener
    }

    fun removePlaybackEventListener() {
        playerEventListenerInternal = null
    }

    /** Player methods */

    fun destroyPlayer() = viewModelScope.launch(Dispatchers.Default) { soundPlayerInternal?.scheduleRelease() }
    fun initPlayer() = viewModelScope.launch(Dispatchers.Default) { soundPlayerInternal?.initialize() }
    fun pause() = viewModelScope.launch(Dispatchers.Default) { soundPlayerInternal?.pause() }
    fun play() = viewModelScope.launch(Dispatchers.Default) { soundPlayerInternal?.play() }
    fun playParallel() = viewModelScope.launch(Dispatchers.Default) { soundPlayerInternal?.playParallel() }
    fun restart() = viewModelScope.launch(Dispatchers.Default) { soundPlayerInternal?.restart() }
    fun stop() = viewModelScope.launch(Dispatchers.Default) { soundPlayerInternal?.stop() }
    fun stopPaused() = viewModelScope.launch(Dispatchers.Default) { soundPlayerInternal?.stopPaused() }
    fun destroyParallelPlayers() =
        viewModelScope.launch(Dispatchers.Default) { soundPlayerInternal?.destroyParallelWrappers() }

    /** Selection: */

    fun enableSelect() = repository.enableSelect()

    fun select() = viewModelScope.launch {
        repository.select(soundInternal.stateIn(viewModelScope).value)
    }

    fun toggleSelect() = viewModelScope.launch {
        if (isSelectedInternal.stateIn(this).value)
            repository.unselect(soundInternal.stateIn(this).value)
        else select()
    }

    fun selectAllFromLastSelected() = viewModelScope.launch(Dispatchers.IO) {
        /** Select all sounds between this viewmodel's sound and the last selected one. */
        val lastSelectedSound = repository.lastSelectedSound.stateIn(this).value

        if (lastSelectedSound != null) {
            val sounds = repository.visibleSounds.stateIn(this).value
            val thisSound = soundInternal.stateIn(this).value
            val thisPos = sounds.indexOf(thisSound)
            val lastSelectedPos = sounds.indexOf(lastSelectedSound)

            if (thisPos > -1 && lastSelectedPos > -1) {
                sounds.subList(min(thisPos, lastSelectedPos), max(thisPos, lastSelectedPos) + 1).forEach { sound ->
                    repository.select(sound)
                }
            }
        }
    }

    override fun onCleared() {
        viewModelScope.launch(Dispatchers.Default) { soundPlayerInternal?.destroy() }
    }

    override fun onPlaybackStarted(currentPosition: Int, duration: Int) {
        isPlayerStartedInternal.value = true
        isPlayerPausedInternal.value = false
        playerEventListenerInternal?.onPlaybackStarted(currentPosition, duration)
    }

    override fun onPlaybackStopped() {
        isPlayerStartedInternal.value = false
        isPlayerPausedInternal.value = false
        playerEventListenerInternal?.onPlaybackStopped()
    }

    override fun onPlaybackPaused(currentPosition: Int, duration: Int) {
        isPlayerStartedInternal.value = false
        isPlayerPausedInternal.value = true
        playerEventListenerInternal?.onPlaybackPaused(currentPosition, duration)
    }

    override fun onTemporaryError(error: String) {
        playerEventListenerInternal?.onTemporaryError(error)
    }

    override fun onPermanentError(error: String) {
        isPlayerErrorInternal.value = true
        playerEventListenerInternal?.onPermanentError(error)
    }
}