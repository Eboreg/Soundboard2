package us.huseli.soundboard2.helpers

import androidx.annotation.IntRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlin.math.max

class SoundPlayer(private val coroutineScope: CoroutineScope) : LoggingObject {
    enum class State { IDLE, STARTED, PAUSED, ERROR }

    private val _wrapper = MediaPlayerWrapper(coroutineScope)
    private val _parallelWrappers = MutableStateFlow<List<MediaPlayerWrapper>>(emptyList())
    private var _path: String? = null
    @IntRange(from = 0, to = 100)
    private var _volume: Int? = null

    private val _wrapperStates = combine(_wrapper.state, _parallelWrappers) { a, b -> b.map { it.state.value } + a }

    val state: Flow<State> = combine(_wrapperStates, _wrapper.hasPermanentError) { states, hasPermanentError ->
        // If main player has permanent error, state is ERROR:
        if (hasPermanentError) State.ERROR
        // If any player is STARTED, state is STARTED:
        else if (states.contains(MediaPlayerWrapper.State.STARTED)) State.STARTED
        // If any player is PAUSED, state is PAUSED:
        else if (states.contains(MediaPlayerWrapper.State.PAUSED)) State.PAUSED
        // Otherwise, state is IDLE:
        else State.IDLE
    }

    val permanentError: Flow<String> = _wrapper.permanentError
    val durationFlow: Flow<Int> = _wrapper.durationFlow
    val duration: Int?  // In milliseconds
        get() = _wrapper.duration
    val currentPosition: Int
        get() = max(_wrapper.currentPosition, _parallelWrappers.value.maxOfOrNull { it.currentPosition } ?: 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val temporaryError: Flow<String> = merge(
        _wrapper.temporaryError,
        _parallelWrappers.flatMapLatest { list -> list.map { it.temporaryError }.merge() }
    )

    private fun createParallelWrapper(): MediaPlayerWrapper {
        val wrapper = MediaPlayerWrapper(coroutineScope)

        wrapper.setOnStateChangeListener { wr, state ->
            if (
                state in listOf(
                    MediaPlayerWrapper.State.ERROR,
                    MediaPlayerWrapper.State.STOPPED,
                    MediaPlayerWrapper.State.PAUSED,
                    MediaPlayerWrapper.State.PLAYBACK_COMPLETED
                )
            ) {
                wr.destroy()
                _parallelWrappers.value -= wr
            }
        }

        _parallelWrappers.value += wrapper
        wrapper.setPath(_path)
        _volume?.let { wrapper.setVolume(it) }
        return wrapper
    }

    fun destroy() {
        // This should already have been done, but just in case:
        destroyParallelWrappers()
        _wrapper.destroy()
    }

    fun destroyParallelWrappers() {
        _parallelWrappers.value.forEach { it.destroy() }
        _parallelWrappers.value = emptyList()
    }

    fun pause() {
        _wrapper.pause()
        // They should already have been destroyed, but anyway:
        destroyParallelWrappers()
    }

    fun playParallel() {
        val wrapper = if (_wrapper.state.value != MediaPlayerWrapper.State.STARTED) _wrapper
        else _parallelWrappers.value.firstOrNull {
            it.state.value == MediaPlayerWrapper.State.PREPARED
        } ?: createParallelWrapper()

        wrapper.play()

        // Preemptively create a new parallel player for the sake of low latency; it should get destroyed by
        // SoundViewHolder when repress mode changes to anything other than OVERLAP.
        if (_parallelWrappers.value.none { it.state.value == MediaPlayerWrapper.State.PREPARED }) createParallelWrapper()
    }

    fun play() = _wrapper.play()

    fun restart() {
        /** If playing, stop and start again from the beginning. Otherwise, just start. */
        _wrapper.restart()
        // They should already have been destroyed, but anyway:
        destroyParallelWrappers()
    }

    fun scheduleInit() = _wrapper.scheduleInit()

    fun scheduleReset() = _wrapper.scheduleReset()

    fun setPath(path: String?) {
        if (path != _path) {
            _path = path
            _wrapper.setPath(path)
        }
    }

    fun setVolume(@IntRange(from = 0, to = 100) volume: Int) {
        if (volume != _volume) {
            _volume = volume
            _wrapper.setVolume(volume)
            _parallelWrappers.value.forEach { it.setVolume(volume) }
        }
    }

    fun stop() {
        _wrapper.stop()
        destroyParallelWrappers()
    }

    fun stopPaused() {
        if (_wrapper.state.value == MediaPlayerWrapper.State.PAUSED) stop()
        else destroyParallelWrappers()
    }
}