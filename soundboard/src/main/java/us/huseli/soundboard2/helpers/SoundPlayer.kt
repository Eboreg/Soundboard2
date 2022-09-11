package us.huseli.soundboard2.helpers

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import us.huseli.soundboard2.Enums.PlayState

class SoundPlayer(path: String? = null, volume: Int? = null) : LoggingObject {
    private val _player = MediaPlayerWrapper()
    private val _parallelPlayers = MutableStateFlow<List<MediaPlayerWrapper>>(emptyList())
    private var _path: String? = null
    private var _volume: Int? = null

    // This SHOULD produce a flow of State arrays, with one array entry for
    // each of the current _parallelPlayers. So a new array will be emitted
    // whenever any of those players change state.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val _parallelPlayerStates: Flow<Array<MediaPlayerWrapper.State>> = _parallelPlayers.flatMapLatest { players ->
        combine(players.map { it.state }) {
            it
        }.onStart {
            emit(emptyArray())
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _parallelPlayerPositions: Flow<Array<Int?>> = _parallelPlayers.flatMapLatest { players ->
        combine(players.map { it.currentPosition }) { it }.onStart { emit(emptyArray()) }
    }

    // This SHOULD, then, produce a flow of State arrays for the main _player
    // and the current _parallelPlayers, combined.
    private val _playerStates = combine(_player.state, _parallelPlayerStates) { a, b -> b + a }

    val state = _playerStates.map { states ->
        // If any player is STARTED, state is STARTED:
        if (states.contains(MediaPlayerWrapper.State.STARTED)) PlayState.STARTED
        // If any player is PAUSED, state is PAUSED:
        else if (states.contains(MediaPlayerWrapper.State.PAUSED)) PlayState.PAUSED
        // Otherwise, state is IDLE:
        else PlayState.IDLE
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val error = merge(
        _player.error,
        _parallelPlayers.flatMapLatest { list -> list.map { it.error }.merge() }
    )

    val currentPosition = combine(_player.currentPosition, _parallelPlayerPositions) { position, parallelPositions ->
        (parallelPositions + position).filterNotNull().maxOrNull()
    }

    init {
        _player.setOnErrorListener { it.reset() }
        if (path != null) setPath(path)
        if (volume != null) setVolume(volume)
    }

    private fun _startParallel() {
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
                mp.release()
                _parallelPlayers.value -= mp
            }
        }

        try {
            _parallelPlayers.value += player
            player.setPath(_path)
            _volume?.let { player.setVolume(it.toFloat() / 100) }
            player.play()
        } catch (e: Exception) {
            player.release()
            _parallelPlayers.value -= player
        }
    }

    fun start(allowParallel: Boolean = false) {
        if (_player.state.value == MediaPlayerWrapper.State.STARTED) {
            if (allowParallel) _startParallel()
        }
        else _player.play()
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun setPath(path: String?) {
        if (path != _path) {
            _path = path
            _player.setPath(path)
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun setVolume(volume: Int) {
        if (volume != _volume) {
            _volume = volume
            _player.setVolume(volume.toFloat() / 100)
        }
    }

    fun pause() {
        _parallelPlayers.value.forEach { it.stop() }
        _player.pause()
    }

    fun restart() {
        /** If playing, stop and start again from the beginning. Otherwise, just start. */
        _parallelPlayers.value.forEach { it.stop() }
        if (_player.isPlaying) _player.stop()
        start()
    }

    fun stop(onlyPaused: Boolean = false) {
        log("stop: _path=$_path, onlyPaused=$onlyPaused, _player.isPaused=${_player.isPaused}, _player.isPlaying=${_player.isPlaying}")
        _parallelPlayers.value.forEach { if (!onlyPaused || !it.isPlaying) it.stop() }
        if (_player.isPaused || (!onlyPaused && _player.isPlaying)) _player.stop()
    }

    fun release() {
        // This should already have been done, but just in case:
        _parallelPlayers.value.forEach {
            it.reset()
            it.release()
            _parallelPlayers.value -= it
        }
        _player.reset()
        _player.release()
    }
}