package us.huseli.soundboard2.helpers

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.SyncParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import us.huseli.soundboard2.Enums.PlayState
import java.lang.Integer.min
import kotlin.math.roundToInt

class SoundPlayer(private val scope: CoroutineScope) : LoggingObject {
    private enum class InternalState { IDLE, INITIALIZED, PREPARED, STARTED, PAUSED, STOPPED, PLAYBACK_COMPLETED, ERROR, }

    private val _player = MediaPlayer()
    private val _parallelPlayers = mutableListOf<MediaPlayer>()
    private val _state = MutableStateFlow(PlayState.IDLE)
    private val _error = MutableStateFlow<String?>(null)
    private var _internalState = InternalState.IDLE

    val state = _state.asStateFlow()
    val error = _error.asStateFlow()

    init {
        _player.setOnErrorListener { mp, what, extra ->
            _error.value = _mediaPlayerErrorToString(what, extra)
            _state.value = PlayState.ERROR
            _internalState = InternalState.ERROR
            mp.reset()
            _internalState = InternalState.IDLE
            true
        }

        _player.setOnCompletionListener { mp ->
            _internalState = InternalState.PLAYBACK_COMPLETED
            mp.reset()
            _internalState = InternalState.IDLE
            if (_parallelPlayers.size == 0) _state.value = PlayState.IDLE
        }
    }

    private fun _mediaPlayerErrorToString(what: Int, extra: Int): String {
        val whatStr = when (what) {
            MediaPlayer.MEDIA_ERROR_UNKNOWN -> "Unspecified media player error"
            MediaPlayer.MEDIA_ERROR_SERVER_DIED -> "Media server died"
            else -> "Other ($what)"
        }
        val extraStr = when (extra) {
            MediaPlayer.MEDIA_ERROR_IO -> "File or network related operation error"
            MediaPlayer.MEDIA_ERROR_MALFORMED -> "Bitstream is not conforming to the related coding standard or file spec"
            MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> "Bitstream is conforming to the related coding standard or file spec, but the media framework does not support the feature"
            MediaPlayer.MEDIA_ERROR_TIMED_OUT -> "Some operation takes too long to complete, usually more than 3-5 seconds"
            else -> "Other ($extra)"
        }
        return "$whatStr: $extraStr"
    }

    private fun _startParallel(path: String?, volume: Float) = scope.launch(Dispatchers.IO) {
        val player = MediaPlayer()

        player.setOnErrorListener { mp, what, extra ->
            _error.value = _mediaPlayerErrorToString(what, extra)
            mp.reset()
            mp.release()
            _parallelPlayers.remove(mp)
            true
        }

        player.setOnCompletionListener { mp ->
            mp.reset()
            mp.release()
            _parallelPlayers.remove(mp)
            if (_parallelPlayers.size == 0 && !_player.isPlaying) _state.value = PlayState.IDLE
        }

        try {
            player.setDataSource(path)
            player.prepare()
            player.setVolume(volume, volume)
            player.start()
            _parallelPlayers.add(player)
        }
        catch (e: Exception) {
            _error.value = e.toString()
        }
    }

    private fun _handleException(e: Exception) {
        /** Only for the main _player, not the parallel ones. */
        _internalState = InternalState.ERROR
        _error.value = e.toString()
        _player.reset()
        _internalState = InternalState.IDLE
        _state.value = PlayState.IDLE
    }

    private fun _start(volume: Float) {
        /** Presupposes that player is already in PREPARED state. */
        _player.syncParams.tolerance = 0.1f
        _player.setVolume(volume, volume)
        _player.start()
        _internalState = InternalState.STARTED
        _state.value = PlayState.STARTED
    }

    fun getCurrentPositionPercent(): Int? {
        if (listOf(PlayState.STARTED, PlayState.PAUSED).contains(_state.value)) {
            val player = _parallelPlayers.lastOrNull() ?: _player
            val pos = player.currentPosition
            val duration = player.duration
            return if (duration > 0) min(((pos.toDouble() / duration) * 100).roundToInt(), 100) else 0
        }
        return null
    }

    fun start(path: String?, volume: Float, allowParallel: Boolean = false) {
        if (_player.isPlaying && allowParallel)
            _startParallel(path, volume)
        else {
            if (listOf(InternalState.IDLE, InternalState.STOPPED, InternalState.INITIALIZED).contains(_internalState)) {
                // Player needs to be prepared, which is a blocking procedure,
                // so do it (and the playing) in a coroutine.
                scope.launch(Dispatchers.IO) {
                    try {
                        if (_internalState == InternalState.IDLE) {
                            _player.setDataSource(path)
                            _internalState = InternalState.INITIALIZED
                        }
                        if (listOf(InternalState.STOPPED, InternalState.INITIALIZED).contains(_internalState)) {
                            _player.prepare()
                            _internalState = InternalState.PREPARED
                        }
                        _start(volume)
                    }
                    catch (e: Exception) { _handleException(e) }
                }
            }
            else {
                // Player is already prepared; do without coroutine.
                try { _start(volume) }
                catch (e: Exception) { _handleException(e) }
            }
        }
    }

    fun pause() {
        _parallelPlayers.forEach {
            it.stop()
            _parallelPlayers.remove(it)
        }
        if (_player.isPlaying) {
            _player.pause()
            _internalState = InternalState.PAUSED
            _state.value = PlayState.PAUSED
        }
    }

    fun restart(path: String?, volume: Float) {
        /** If playing, stop and start again from the beginning. Otherwise, just start. */
        _parallelPlayers.forEach {
            it.stop()
            _parallelPlayers.remove(it)
        }
        if (_player.isPlaying) {
            _player.stop()
            _internalState = InternalState.STOPPED
        }
        start(path, volume)
    }

    fun stop(onlyPaused: Boolean = false) {
        _parallelPlayers.forEach {
            if (!onlyPaused || !it.isPlaying) {
                it.stop()
                _parallelPlayers.remove(it)
            }
        }
        if (!onlyPaused || !_player.isPlaying) {
            if (listOf(InternalState.STARTED, InternalState.PAUSED).contains(_internalState)) {
                _player.stop()
                _internalState = InternalState.STOPPED
            }
            if (_internalState != InternalState.IDLE) {
                _player.reset()
                _internalState = InternalState.IDLE
            }
            _state.value = PlayState.IDLE
        }
    }

    fun release() {
        // This should already have been done, but just in case:
        _parallelPlayers.forEach {
            it.reset()

            it.release()
        }
        _player.reset()
        _player.release()
    }
}