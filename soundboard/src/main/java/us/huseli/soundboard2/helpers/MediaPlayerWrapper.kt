package us.huseli.soundboard2.helpers

import android.media.MediaPlayer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
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
        fun onError(mp: MediaPlayerWrapper, what: Int, extra: Int): Boolean
    }

    fun interface OnCompletionListener {
        fun onCompletion(mp: MediaPlayerWrapper)
    }

    fun interface OnStateChangeListener {
        fun onStateChange(mp: MediaPlayerWrapper, state: State)
    }

    private val _mp: MediaPlayer = MediaPlayer()
    private val _state = MutableStateFlow(State.IDLE)
    private val _error = MutableStateFlow<String?>(null)
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
    val error = _error.filterNotNull()  // Only updated when there actually is an error
    val isPlaying = _state.value == State.STARTED
    val isPaused = _state.value == State.PAUSED
    val duration: Int?
        get() = if (_state.value in listOf(State.IDLE, State.INITIALIZED, State.ERROR)) null else _mp.duration

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentPosition: Flow<Int?> = _state.flatMapLatest { state ->
        // log("currentPosition: state=$state")
        flow {
            emit(_currentPosition)
            while (state == State.STARTED) {
                // log("currentPosition: state=$state, _currentPosition=$_currentPosition")
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
            // if (e is IllegalStateException) _state.value = State.ERROR
            _error.value = "Error on pause(): $e"
            throw e
        }
    }

    fun release() {
        reset()
        changeState(State.END)
        _mp.release()
    }

    fun reset() {
        changeState(State.IDLE)
        _mp.reset()
    }

    fun play() {
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
        try {
            if (_state.value in listOf(State.STARTED, State.PAUSED)) changeState(State.STOPPED)
            _mp.stop()
        } catch (e: Exception) {
            // if (e is IllegalStateException) _state.value = State.ERROR
            _error.value = "Error on stop(): $e"
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
        try {
            _mp.syncParams.tolerance = 0.1f
            if (_state.value in listOf(State.INITIALIZED, State.STOPPED)) changeState(State.PREPARED)
            _mp.prepare()
        } catch (e: Exception) {
            _error.value = "Error on prepare(): $e"
            throw e
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
        try {
            _mp.setDataSource(path)
            changeState(State.INITIALIZED)
        } catch (e: Exception) {
            _error.value = "Error on setDataSource(): $e"
            // throw e
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
        try {
            if (_state.value in listOf(State.PREPARED, State.PAUSED, State.PLAYBACK_COMPLETED)) changeState(State.STARTED)
            _mp.start()
        } catch (e: Exception) {
            // if (e is IllegalStateException) _state.value = State.ERROR
            _error.value = "Error on start(): $e"
            // throw e
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
        changeState(State.ERROR)
        _error.value = "$whatStr: $extraStr"
        return _onErrorListener?.onError(this, what, extra) ?: true
    }
}
