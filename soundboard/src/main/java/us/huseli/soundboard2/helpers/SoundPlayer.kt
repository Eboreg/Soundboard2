package us.huseli.soundboard2.helpers

import android.os.Handler
import androidx.annotation.IntRange
import androidx.annotation.MainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlin.math.max

class SoundPlayer(private val coroutineScope: CoroutineScope, private val audioThreadHandler: Handler) : LoggingObject {
    enum class State { IDLE, STARTED, PAUSED, ERROR }

    private val _player = MediaPlayerWrapper(coroutineScope)
    private val _parallelPlayers = MutableStateFlow<List<MediaPlayerWrapper>>(emptyList())
    private var _path: String? = null
    @IntRange(from = 0, to = 100)
    private var _volume: Int? = null

    private val _playerStates = combine(_player.state, _parallelPlayers) { a, b -> b.map { it.state.value } + a }

    val state: Flow<State> = combine(_playerStates, _player.hasPermanentError) { states, hasPermanentError ->
        // If main player has permanent error, state is ERROR:
        if (hasPermanentError) State.ERROR
        // If any player is STARTED, state is STARTED:
        else if (states.contains(MediaPlayerWrapper.State.STARTED)) State.STARTED
        // If any player is PAUSED, state is PAUSED:
        else if (states.contains(MediaPlayerWrapper.State.PAUSED)) State.PAUSED
        // Otherwise, state is IDLE:
        else State.IDLE
    }

    val permanentError: Flow<String> = _player.permanentError
    val durationFlow: Flow<Int> = _player.durationFlow
    val duration: Int?  // In milliseconds
        get() = _player.duration
    val currentPosition: Int
        get() = max(_player.currentPosition, _parallelPlayers.value.maxOfOrNull { it.currentPosition } ?: 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val temporaryError: Flow<String> = merge(
        _player.temporaryError,
        _parallelPlayers.flatMapLatest { list -> list.map { it.temporaryError }.merge() }
    )

    private fun createParallelPlayer(): MediaPlayerWrapper {
        val player = MediaPlayerWrapper(coroutineScope)

        player.setOnStateChangeListener { mp, state ->
            if (
                state in listOf(
                    MediaPlayerWrapper.State.ERROR,
                    MediaPlayerWrapper.State.STOPPED,
                    MediaPlayerWrapper.State.PAUSED,
                    MediaPlayerWrapper.State.PLAYBACK_COMPLETED
                )
            ) {
                mp.destroy()
                _parallelPlayers.value -= mp
            }
        }

        _parallelPlayers.value += player
        player.setPath(_path)
        _volume?.let { player.setVolume(it) }
        return player
    }

    fun destroyParallelPlayers() {
        _parallelPlayers.value.forEach { it.destroy() }
        _parallelPlayers.value = emptyList()
    }

    fun playParallel() {
        val player = if (_player.state.value != MediaPlayerWrapper.State.STARTED) _player
        else _parallelPlayers.value.firstOrNull {
            it.state.value == MediaPlayerWrapper.State.PREPARED
        } ?: createParallelPlayer()

        audioThreadHandler.post {
            player.play()
        }

        // Preemptively create a new parallel player for the sake of low latency; it should get destroyed by
        // SoundViewHolder when repress mode changes to anything other than OVERLAP.
        if (_parallelPlayers.value.none { it.state.value == MediaPlayerWrapper.State.PREPARED }) createParallelPlayer()
    }

    fun play() = audioThreadHandler.post { _player.play() }

    fun setPath(path: String?) {
        if (path != _path) {
            _path = path
            _player.setPath(path)
        }
    }

    fun setVolume(@IntRange(from = 0, to = 100) volume: Int) {
        if (volume != _volume) {
            _volume = volume
            _player.setVolume(volume)
            _parallelPlayers.value.forEach { it.setVolume(volume) }
        }
    }

    fun pause() {
        audioThreadHandler.post {
            _player.pause()
        }
        // They should already have been destroyed, but anyway:
        destroyParallelPlayers()
    }

    fun restart() {
        /** If playing, stop and start again from the beginning. Otherwise, just start. */
        audioThreadHandler.post {
            _player.restart()
        }
        // They should already have been destroyed, but anyway:
        destroyParallelPlayers()
    }

    fun stop() {
        audioThreadHandler.post {
            _player.stop()
        }
        destroyParallelPlayers()
    }

    @MainThread
    fun stopPaused() {
        if (_player.state.value == MediaPlayerWrapper.State.PAUSED) audioThreadHandler.post { stop() }
        else destroyParallelPlayers()
    }

    @MainThread
    fun destroy() {
        // This should already have been done, but just in case:
        destroyParallelPlayers()
        _player.destroy()
    }
}