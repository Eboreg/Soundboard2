package us.huseli.soundboard2.helpers

import android.media.MediaPlayer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.Integer.min
import kotlin.math.roundToInt

class MediaPlayerWrapper :
    LoggingObject,
    MediaPlayer.OnCompletionListener,
    MediaPlayer.OnErrorListener
{
    /** Expose the state and any error as a flow. */
    enum class State { IDLE, INITIALIZED, PREPARED, STARTED, PAUSED, STOPPED, PLAYBACK_COMPLETED, ERROR, END }

    fun interface OnErrorListener {
        fun onError(mp: MediaPlayerWrapper)
    }

    fun interface OnCompletionListener {
        fun onCompletion(mp: MediaPlayerWrapper)
    }

    fun interface OnStateChangeListener {
        fun onStateChange(mp: MediaPlayerWrapper, state: State)
    }

    private val _mp: MediaPlayer = MediaPlayer()
    private val _state = MutableStateFlow(State.IDLE)
    private val _error = Channel<String>()
    private var _onCompletionListener: OnCompletionListener? = null
    private var _onErrorListener: OnErrorListener? = null
    private var _onStateChangeListener: OnStateChangeListener? = null
    private var _volume: Float? = null
    private var _path: String? = null
    private val _currentPosition: Int?
        get() = if (_state.value in listOf(State.STARTED, State.PAUSED))
            if (_mp.duration > 0) min(((_mp.currentPosition.toDouble() / _mp.duration) * 100).roundToInt(), 100) else 0
        else null

    val state: StateFlow<State> = _state
    val error: Flow<String> = _error.receiveAsFlow()
    val isPlaying: Boolean
        get() = _state.value == State.STARTED
    val isPaused: Boolean
        get() = _state.value == State.PAUSED
    val duration: Int?
        get() = if (_state.value in listOf(State.IDLE, State.INITIALIZED, State.ERROR)) null else _mp.duration

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentPosition: Flow<Int?> = _state.flatMapLatest { state ->
        flow {
            emit(_currentPosition)
            while (state == State.STARTED) {
                delay(200)
                emit(_currentPosition)
            }
        }
    }

    init {
        _mp.setOnCompletionListener(this)
        _mp.setOnErrorListener(this)
    }

    /** PUBLIC METHODS ******************************************************/

    /**
     * Valid states: {Started, Paused, PlaybackCompleted}
     * Invalid states: {Idle, Initialized, Prepared, Stopped, Error}
     *
     * Successful invoke of this method in a valid state transfers the object to the Paused state. Calling this method
     * in an invalid state transfers the object to the Error state.
     *
     * If this method is called right after a MediaPlayer object is constructed, the user supplied callback method
     * OnErrorListener.onError() won't be called by the internal player engine and the object state remains unchanged;
     * but if these methods are called right after reset(), the user supplied callback method OnErrorListener.onError()
     * will be invoked by the internal player engine and the object will be transfered to the Error state.
     */
    fun pause() {
        try {
            if (_state.value == State.STARTED) changeState(State.PAUSED)
            _mp.pause()
        } catch (e: Exception) {
            changeState(State.ERROR)
            _error.trySend("Error on pause(): $e")
        }
    }

    fun release() {
        reset()
        changeState(State.END)
        _mp.release()
    }

    fun reset() {
        log("reset(): _state=${_state.value}")
        _mp.reset()
        changeState(State.IDLE)
    }

    fun play() {
        log("play(): _state=${_state.value}")
        if (_state.value == State.ERROR) reset()
        if (_state.value == State.IDLE) setDataSource(_path)
        if (_state.value == State.STARTED) stop()
        if (_state.value in listOf(State.STOPPED, State.INITIALIZED)) prepare()
        setVolume(_volume)
        start()
    }

    fun setOnErrorListener(listener: OnErrorListener) {
        _onErrorListener = listener
    }

    @Suppress("unused")
    fun setOnCompletionListener(listener: OnCompletionListener) {
        _onCompletionListener = listener
    }

    fun setOnStateChangeListener(listener: OnStateChangeListener) {
        _onStateChangeListener = listener
    }

    fun setPath(value: String?) {
        if (value != _path) {
            _path = value
            if (_state.value == State.ERROR) reset()
            setDataSource(value)
        }
    }

    fun setVolume(value: Float?) {
        log("setVolume(): value=$value, _state=${_state.value}")
        if (value != null) {
            _volume = value
            _mp.setVolume(value, value)
        }
    }

    /**
     * Valid states: {Prepared, Started, Stopped, Paused, PlaybackCompleted}
     * Invalid states: {Idle, Initialized, Error}
     *
     * Successful invoke of this method in a valid state transfers the object to the Stopped state. Calling this
     * method in an invalid state transfers the object to the Error state.
     *
     * If this method is called right after a MediaPlayer object is constructed, the user supplied callback method
     * OnErrorListener.onError() won't be called by the internal player engine and the object state remains unchanged;
     * but if these methods are called right after reset(), the user supplied callback method OnErrorListener.onError()
     * will be invoked by the internal player engine and the object will be transfered to the Error state.
     *
     * Local change: Will not do _mp.stop() if state is PREPARED or PLAYBACK_COMPLETED, since that would only force us
     * to run prepare() again for no good reason.
     */
    fun stop() {
        log("stop(): _state=${_state.value}")
        try {
            _mp.stop()
            if (_state.value in listOf(State.STARTED, State.PAUSED)) changeState(State.STOPPED)
        } catch (e: Exception) {
            changeState(State.ERROR)
            _error.trySend("Error on stop(): $e")
        }
    }

    /** PRIVATE METHODS *****************************************************/

    private fun changeState(state: State) {
        if (state != _state.value) {
            _state.value = state
            _onStateChangeListener?.onStateChange(this, state)
        }
    }

    /**
     * Valid states: {Initialized, Stopped}
     * Invalid states: {Idle, Prepared, Started, Paused, PlaybackCompleted, Error}
     *
     * Successful invoke of this method in a valid state transfers the object to the Prepared state. Calling this
     * method in an invalid state throws an IllegalStateException.
     *
     * If this method is called right after a MediaPlayer object is constructed, the user supplied callback method
     * OnErrorListener.onError() won't be called by the internal player engine and the object state remains unchanged;
     * but if these methods are called right after reset(), the user supplied callback method OnErrorListener.onError()
     * will be invoked by the internal player engine and the object will be transfered to the Error state.
     *
     * @throws IllegalStateException
     * @throws IOException
     */
    private fun prepare() {
        log("prepare(): _state=${_state.value}")
        try {
            _mp.syncParams.tolerance = 0.1f
            _mp.prepare()
            if (_state.value in listOf(State.INITIALIZED, State.STOPPED)) changeState(State.PREPARED)
        } catch (e: Exception) {
            changeState(State.ERROR)
            _error.trySend("Error on prepare(): $e")
        }
    }

    /**
     * Valid states: {Idle}
     * Invalid states: {Initialized, Prepared, Started, Paused, Stopped, PlaybackCompleted, Error}
     *
     * Successful invoke of this method in a valid state transfers the object to the Initialized state. Calling this
     * method in an invalid state throws an IllegalStateException.
     */
    private fun setDataSource(path: String?) {
        log("setDataSource(): path=$path, _state=${_state.value}")
        try {
            _mp.setDataSource(path)
            changeState(State.INITIALIZED)
        }
        catch (e: Exception) {
            log("setDataSource(): e=$e, path=$path, _state=${_state.value}")
            changeState(State.ERROR)
            _error.trySend(
                if (e is FileNotFoundException) "File not found: $path"
                else "Error on setDataSource(): $e"
            )
        }
    }

    /**
     * Valid states: {Prepared, Started, Paused, PlaybackCompleted}
     * Invalid states: {Idle, Initialized, Stopped, Error}
     *
     * Successful invoke of this method in a valid state transfers the object to the Started state. Calling this method
     * in an invalid state transfers the object to the Error state.
     *
     * If this method is called right after a MediaPlayer object is constructed, the user supplied callback method
     * OnErrorListener.onError() won't be called by the internal player engine and the object state remains unchanged;
     * but if these methods are called right after reset(), the user supplied callback method OnErrorListener.onError()
     * will be invoked by the internal player engine and the object will be transfered to the Error state.
     */
    private fun start() {
        log("start(): _state=${_state.value}")
        try {
            _mp.start()
            if (_state.value in listOf(State.PREPARED, State.PAUSED, State.PLAYBACK_COMPLETED)) changeState(State.STARTED)
        } catch (e: Exception) {
            changeState(State.ERROR)
            _error.trySend("Error on start(): $e")
        }
    }

    /** OVERRIDDEN METHODS ***************************************************/

    override fun onCompletion(mp: MediaPlayer?) {
        changeState(State.PLAYBACK_COMPLETED)
        _onCompletionListener?.onCompletion(this)
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        val whatStr = when (what) {
            MediaPlayer.MEDIA_ERROR_UNKNOWN -> "Unspecified media player error"
            MediaPlayer.MEDIA_ERROR_SERVER_DIED -> "Media server died"
            else -> "Other ($what)"
        }
        val extraStr = when (extra) {
            MediaPlayer.MEDIA_ERROR_IO -> "File or network error"
            MediaPlayer.MEDIA_ERROR_MALFORMED -> "Malformed file"
            MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> "Unsupported file"
            MediaPlayer.MEDIA_ERROR_TIMED_OUT -> "Timeout"
            else -> "Other ($extra)"
        }
        log("onError(): what=$what, extra=$extra, _state=${_state.value}")
        if (_state.value != State.ERROR) _error.trySend("$whatStr: $extraStr")
        changeState(State.ERROR)
        _onErrorListener?.onError(this)
        return true
    }
}
