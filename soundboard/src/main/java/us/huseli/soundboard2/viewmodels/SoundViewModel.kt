package us.huseli.soundboard2.viewmodels

import android.graphics.Color
import android.os.Handler
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
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
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(FlowPreview::class)
@HiltViewModel
class SoundViewModel @Inject constructor(
    private val repository: SoundRepository,
    private val settingsRepository: SettingsRepository,
    colorHelper: ColorHelper,
    audioThreadHandler: Handler
) : LoggingObject, ViewModel() {
    private val soundIdInternal = MutableStateFlow<Int?>(null)
    private val soundInternal: Flow<SoundExtended> = soundIdInternal.filterNotNull().flatMapConcat {
        repository.get(it).filterNotNull()
    }
    // private val soundInternal: Flow<SoundExtended> = repository.get(soundIdInternal).filterNotNull()
    private val playerInternal = SoundPlayer(viewModelScope, audioThreadHandler)
    private val decimalFormatInternal = DecimalFormat(".#").also {
        val symbols = it.decimalFormatSymbols
        symbols.decimalSeparator = '.'
        it.decimalFormatSymbols = symbols
    }

    @ColorInt
    val backgroundColor: LiveData<Int> = soundInternal.map {
        if (it.backgroundColor == Color.TRANSPARENT) it.categoryColor else it.backgroundColor
    }.asLiveData()
    val durationString = soundInternal.map { sound ->
        when {
            sound.duration > -1 && sound.duration < 950 -> decimalFormatInternal.format(sound.duration.toDouble() / 1000) + "s"
            sound.duration > -1 -> (sound.duration.toDouble() / 1000).roundToInt().toString() + "s"
            else -> null
        }
    }.asLiveData()
    val isAnimationEnabled: LiveData<Boolean> = settingsRepository.isAnimationEnabled.asLiveData()
    val name: LiveData<String> = soundInternal.map { it.name }.asLiveData()
    val path: LiveData<String> = soundInternal.map { it.uri.path }.filterNotNull().asLiveData()
    val playerPermanentError: LiveData<String> = playerInternal.permanentError.asLiveData()
    val playerState: LiveData<SoundPlayer.State> = playerInternal.state.asLiveData()
    val playerTemporaryError: LiveData<String> = playerInternal.temporaryError.asLiveData()
    val repressMode: LiveData<RepressMode> = settingsRepository.repressMode.asLiveData()
    val screenHeightPx: Int
        get() = settingsRepository.screenHeightPx
    val scrollEndSignal = settingsRepository.scrollEndSignal.asLiveData()
    val secondaryBackgroundColor: LiveData<Int> = backgroundColor.map { colorHelper.darkenOrBrighten(it) }
    @ColorInt
    val textColor: LiveData<Int> = backgroundColor.map { colorHelper.getColorOnBackground(it) }
    @IntRange(from = 0, to = 100)
    val volume: LiveData<Int> = soundInternal.map { it.volume }.asLiveData()

    val duration: Int?
        get() = playerInternal.duration
    val currentPosition: Int
        get() = playerInternal.currentPosition

    init {
        viewModelScope.launch {
            soundInternal.collect { sound ->
                playerInternal.setPath(sound.uri.path)
                playerInternal.setVolume(sound.volume)
            }
        }
        viewModelScope.launch {
            // If duration from player differs from duration in DB, update DB with
            // the (hopefully) more correct player duration.
            combineTransform(playerInternal.durationFlow, soundInternal) { duration, sound ->
                if (duration.toLong() != sound.duration) emit(sound.clone(duration = duration.toLong()))
            }.collect { sound -> repository.update(listOf(sound)) }
        }
    }

    /** State booleans etc. */
    val isPlayerError: LiveData<Boolean> = playerInternal.state.map { it == SoundPlayer.State.ERROR }.asLiveData()
    val isPlayerPaused: LiveData<Boolean> = playerInternal.state.map { it == SoundPlayer.State.PAUSED }.asLiveData()
    val isPlayerStarted: LiveData<Boolean> = playerInternal.state.map { it == SoundPlayer.State.STARTED }.asLiveData()
    val isSelectEnabled: LiveData<Boolean> = repository.isSelectEnabled.asLiveData()
    val isSelected: LiveData<Boolean> =
        repository.selectedSoundIds.map { it.contains(soundIdInternal.value) }.asLiveData()

    fun setSoundId(soundId: Int) {
        soundIdInternal.value = soundId
    }

    fun destroyParallelPlayers() = playerInternal.destroyParallelPlayers()
    fun pause() = playerInternal.pause()
    fun play() = playerInternal.play()
    fun playParallel() = playerInternal.playParallel()
    fun restart() = playerInternal.restart()
    fun schedulePlayerInit() = playerInternal.scheduleInit()
    fun schedulePlayerReset() = playerInternal.scheduleReset()
    fun stop() = playerInternal.stop()
    fun stopPaused() = playerInternal.stopPaused()

    fun enableSelect() = repository.enableSelect()
    fun select() = soundIdInternal.value?.let { repository.select(it) }
    fun unselect() = soundIdInternal.value?.let { repository.unselect(it) }

    fun selectAllFromLastSelected() = viewModelScope.launch {
        /** Select all sounds between this viewmodel's sound and the last selected one. */
        val lastSelectedId = repository.lastSelectedId.stateIn(viewModelScope).value

        if (lastSelectedId != null) {
            val soundIds = repository.filteredSoundIdsOrdered.stateIn(viewModelScope).value
            val thisPos = soundIds.indexOf(soundIdInternal.value)
            val lastSelectedPos = soundIds.indexOf(lastSelectedId)

            if (thisPos > -1 && lastSelectedPos > -1) {
                soundIds.subList(min(thisPos, lastSelectedPos), max(thisPos, lastSelectedPos) + 1).forEach {
                    repository.select(it)
                }
            }
        }
    }

    override fun onCleared() = playerInternal.destroy()
}