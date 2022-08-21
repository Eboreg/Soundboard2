package us.huseli.soundboard2.helpers

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import us.huseli.soundboard2.Enums.PlayState

class SoundPlayer : LoggingObject {
    private val _player = MediaPlayerWrapper()
    private val _parallelPlayers = MutableStateFlow<List<MediaPlayerWrapper>>(emptyList())
    private var _path: String? = null
    private var _volume: Float? = null
    private val mutex = Mutex()

    // This SHOULD produce a flow of State arrays, with one array entry for
    // each of the current _parallelPlayers. So a new array will be emitted
    // whenever any of those players change state.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val _parallelPlayerStates: Flow<Array<MediaPlayerWrapper.State>> = _parallelPlayers.flatMapLatest { players ->
        // log("_parallelPlayerStates: players=$players")
        combine(players.map {
            // log("_parallelPlayerStates.flows: it=$it")
            it.state
        }) {
            // log("_parallelPlayerStates.transform: it=$it")
            it
        }.onStart {
            // log("_parallelPlayerStates.onStart: will emit empty array")
            emit(emptyArray())
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _parallelPlayerPositions: Flow<Array<Int?>> = _parallelPlayers.flatMapLatest { players ->
        // log("_parallelPlayerPositions: players=$players")
        combine(players.map { it.currentPosition }) { it }.onStart { emit(emptyArray()) }
    }

    // This SHOULD, then, produce a flow of State arrays for the main _player
    // and the current _parallelPlayers, combined.
    private val _playerStates = combine(_player.state, _parallelPlayerStates) { a, b -> b + a }

    val state = _playerStates.map { states ->
        // log("state: states=${states.map { it.name }}")
        // If any player is STARTED, state is STARTED
        if (states.contains(MediaPlayerWrapper.State.STARTED)) PlayState.STARTED
        // If any player is PAUSED, state is PAUSED
        else if (states.contains(MediaPlayerWrapper.State.PAUSED)) PlayState.PAUSED
        // Otherwise, state is IDLE
        else PlayState.IDLE
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val error = merge(
        _player.error,
        _parallelPlayers.flatMapLatest { list -> list.map { it.error }.merge() }
    )

    val currentPosition = combine(_player.currentPosition, _parallelPlayerPositions) { position, parallelPositions ->
        // log("currentPosition: position=$position, parallelPositions=$parallelPositions")
        (parallelPositions + position).filterNotNull().maxOrNull()
    }

    init {
        _player.setOnErrorListener { mp, _, _ ->
            mp.reset()
            true
        }
        // _player.setOnCompletionListener { mp -> mp.reset() }
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
            player.setVolume(_volume)
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
        else {
            try {
                _player.play()
            } catch (e: Exception) {
                _player.reset()
                // mutex.unlock()
            }
        }
    }

    fun setPath(path: String?) {
        if (path != _path) {
            _path = path
            _player.setPath(path)
        }
    }

    fun setVolume(volume: Float) {
        if (volume != _volume) {
            _volume = volume
            _player.setVolume(volume)
        }
    }

    fun pause() {
        _parallelPlayers.value.forEach {
            it.stop()
            // _parallelPlayers.value -= it
        }
        _player.pause()
    }

    fun restart() {
        /** If playing, stop and start again from the beginning. Otherwise, just start. */
        _parallelPlayers.value.forEach {
            it.stop()
            // _parallelPlayers.value -= it
        }
        if (_player.isPlaying) _player.stop()
        start()
    }

    fun stop(onlyPaused: Boolean = false) {
        _parallelPlayers.value.forEach {
            if (!onlyPaused || !it.isPlaying) {
                it.stop()
                // _parallelPlayers.value -= it
            }
        }
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