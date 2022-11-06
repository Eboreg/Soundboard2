package us.huseli.soundboard2.helpers

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

class SoundPlayer : LoggingObject {
    enum class State { IDLE, STARTED, PAUSED, ERROR }

    private val _player = MediaPlayerWrapper()
    private val _parallelPlayers = MutableStateFlow<List<MediaPlayerWrapper>>(emptyList())
    private var _path: String? = null
    private var _volume: Int? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _parallelPlayerPositions: Flow<Array<Int?>> = _parallelPlayers.flatMapLatest { players ->
        combine(players.map { it.currentPosition }) { it }.onStart { emit(emptyArray()) }
    }

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

    val hasPermanentError = _player.hasPermanentError
    val permanentError = _player.permanentError

    @OptIn(ExperimentalCoroutinesApi::class)
    val temporaryError: Flow<String> = merge(
        _player.temporaryError,
        _parallelPlayers.flatMapLatest { list -> list.map { it.temporaryError }.merge() }
    )

    val currentPosition = combine(_player.currentPosition, _parallelPlayerPositions) { position, parallelPositions ->
        (parallelPositions + position).filterNotNull().minOrNull()
    }

    private fun createParallelPlayer(): MediaPlayerWrapper {
        val player = MediaPlayerWrapper()

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

        player.play()

        // Preemptively create a new parallel player for the sake of low latency; it should get destroyed by
        // SoundViewHolder when repress mode changes to anything other than OVERLAP.
        if (_parallelPlayers.value.none { it.state.value == MediaPlayerWrapper.State.PREPARED }) createParallelPlayer()
    }

    fun play() = _player.play()

    fun setPath(path: String?) {
        if (path != _path) {
            _path = path
            _player.setPath(path)
        }
    }

    fun setVolume(volume: Int) {
        if (volume != _volume) {
            _volume = volume
            _player.setVolume(volume)
            _parallelPlayers.value.forEach { it.setVolume(volume) }
        }
    }

    suspend fun pause() {
        _player.pause()
        // They should already have been destroyed, but anyway:
        destroyParallelPlayers()
    }

    fun restart() {
        /** If playing, stop and start again from the beginning. Otherwise, just start. */
        _player.restart()
        // They should already have been destroyed, but anyway:
        destroyParallelPlayers()
    }

    fun stop() {
        log("stop: _path=$_path, _player.isPaused=${_player.isPaused}, _player.isPlaying=${_player.isPlaying}")
        _player.stop()
        destroyParallelPlayers()
    }

    fun stopPaused() {
        if (_player.isPaused) stop()
        else destroyParallelPlayers()
    }

    fun destroy() {
        // This should already have been done, but just in case:
        destroyParallelPlayers()
        _player.destroy()
    }
}