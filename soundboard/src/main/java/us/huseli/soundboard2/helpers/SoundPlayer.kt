package us.huseli.soundboard2.helpers

import androidx.annotation.IntRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.Integer.max

class SoundPlayer(private val coroutineScope: CoroutineScope) : LoggingObject, PlayerEventListener {
    enum class State { IDLE, STARTED, PAUSED, ERROR }

    private val _wrapper = MediaPlayerWrapper().also { it.setPlaybackEventListener(this) }
    private val _parallelWrappers = mutableListOf<MediaPlayerWrapper>()
    private var _path: String? = null
    @IntRange(from = 0, to = 100)
    private var _volume: Int? = null
    private var _playerEventListener: PlayerEventListener? = null

    val state: State
        get() = (_parallelWrappers + _wrapper).map { it.state }.let { wrapperStates ->
            if (_wrapper.hasPermanentError) State.ERROR
            else if (wrapperStates.contains(MediaPlayerWrapper.State.STARTED)) State.STARTED
            else if (wrapperStates.contains(MediaPlayerWrapper.State.PAUSED)) State.PAUSED
            else State.IDLE
        }

    init {
        // An extra little garbage collector, destroying any surplus wrappers
        // once per second:
        coroutineScope.launch(Dispatchers.Default) {
            while (true) {
                _parallelWrappers.filter { it.state != MediaPlayerWrapper.State.STARTED }.let { idleWrappers ->
                    // Destroy all but one:
                    idleWrappers.subList(0, max(idleWrappers.size - 1, 0)).forEach { idleWrapper ->
                        idleWrapper.destroy()
                        _parallelWrappers -= idleWrapper
                        log("init: destroyed and removed wrapper $idleWrapper. _parallelWrappers after=$_parallelWrappers")
                    }
                }
                delay(1000)
            }
        }
    }

    private fun createParallelWrapper(): MediaPlayerWrapper {
        val wrapper = MediaPlayerWrapper()
        wrapper.setPlaybackEventListener(this)

        wrapper.setStateListener { wr, state ->
            log("onStateChange: wrapper=$wr, state=$state")
            if (
                state in listOf(
                    MediaPlayerWrapper.State.ERROR,
                    MediaPlayerWrapper.State.STOPPED,
                    MediaPlayerWrapper.State.PAUSED,
                    MediaPlayerWrapper.State.PLAYBACK_COMPLETED,
                )
            ) {
                wr.destroy()
                _parallelWrappers -= wr
                log("onStateChange: destroyed and removed wrapper $wr. _parallelWrappers after=$_parallelWrappers")
            }
        }

        _parallelWrappers += wrapper
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
        _parallelWrappers.forEach { it.destroy() }
        _parallelWrappers.clear()
    }

    fun initialize() {
        _wrapper.initialize()
        if (state == State.STARTED) {
            // If playback is already started, call listener callback.
            val startedWrappers = (_parallelWrappers + _wrapper).filter { it.state == MediaPlayerWrapper.State.STARTED }
            val currentPosition = startedWrappers.minOfOrNull { it.currentPosition }
            val duration = startedWrappers.maxOfOrNull { it.duration }
            if (currentPosition != null && duration != null) onPlaybackStarted(currentPosition, duration)
        }
    }

    fun pause() {
        _wrapper.pause()
        // They should already have been destroyed, but anyway:
        destroyParallelWrappers()
    }

    fun playParallel() {
        val wrapper = if (_wrapper.state != MediaPlayerWrapper.State.STARTED) _wrapper
        else _parallelWrappers.firstOrNull {
            it.state != MediaPlayerWrapper.State.STARTED
        } ?: createParallelWrapper()
        wrapper.play()
    }

    fun play() = _wrapper.play()

    fun restart() {
        /** If playing, stop and start again from the beginning. Otherwise, just start. */
        _wrapper.restart()
        // They should already have been destroyed, but anyway:
        destroyParallelWrappers()
    }

    fun scheduleRelease() = coroutineScope.launch {
        while (state in listOf(State.STARTED, State.PAUSED)) delay(100)
        destroyParallelWrappers()
        _wrapper.release()
    }

    fun setPath(path: String?) {
        if (path != _path) {
            _path = path
            _wrapper.setPath(path)
        }
    }

    fun setPlaybackEventListener(listener: PlayerEventListener) {
        _playerEventListener = listener
    }

    fun setVolume(@IntRange(from = 0, to = 100) volume: Int) {
        if (volume != _volume) {
            _volume = volume
            _wrapper.setVolume(volume)
            _parallelWrappers.forEach { it.setVolume(volume) }
        }
    }

    fun stop() {
        _wrapper.stop()
        destroyParallelWrappers()
    }

    fun stopPaused() {
        /** Only stop if paused. */
        if (_wrapper.state == MediaPlayerWrapper.State.PAUSED) stop()
        else destroyParallelWrappers()
    }

    fun stopStartedOrPaused() {
        /** Only stop if started or pauseed. */
        if (_wrapper.state in listOf(MediaPlayerWrapper.State.PAUSED, MediaPlayerWrapper.State.STARTED)) stop()
        else destroyParallelWrappers()
    }

    override fun onPlaybackStarted(currentPosition: Int, duration: Int) {
        _playerEventListener?.onPlaybackStarted(currentPosition, duration)
    }

    override fun onPlaybackStopped() {
        // Only propagate event if no wrapper is still playing:
        if ((_parallelWrappers + _wrapper).none { it.state == MediaPlayerWrapper.State.STARTED })
            _playerEventListener?.onPlaybackStopped()
    }

    override fun onPlaybackPaused(currentPosition: Int, duration: Int) {
        _playerEventListener?.onPlaybackPaused(currentPosition, duration)
    }

    override fun onTemporaryError(error: String) {
        _playerEventListener?.onTemporaryError(error)
    }

    override fun onPermanentError(error: String) {
        _playerEventListener?.onPermanentError(error)
    }
}