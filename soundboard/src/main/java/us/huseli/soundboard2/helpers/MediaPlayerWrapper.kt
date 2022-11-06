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

/**
 * Tests indicate that this table basically seems to be correct:
 * https://developer.android.com/reference/android/media/MediaPlayer#valid-and-invalid-states
 *
 * IllegalStateException is only thrown by prepare() and setDataSource().
 * prepare() does so if called in the Error, Idle, Prepared, Started, Paused, or End states.
 * setDataSource() does so if called in the Initialized, Prepared, Started, Paused, Stopped, or End states.
 *
 * OnErrorListener.onError() does not ever seem to be called as a result of programming errors such as calling a
 * function from the wrong state, but rather by unexpected errors during playback.
 *
 * In onError(), I have never seen any values for `what` and `extra` that are actually mentioned in the docs.
 *
 * IllegalStateExceptions do not put the player in Error state; it seems to stay in its previous state.
 *
 * If we end up in the Error state, the only thing to do is to call reset() or release().
 *
 * Avoid calling these methods while in these states, because it will put us in Error state and force us to reset:
 * - getDuration()      Idle, Initialized
 * - pause()            Idle, Initialized, Prepared, Stopped
 * - seekTo()           Idle, Initialized, Stopped
 * - start()            Idle, Initialized, Stopped
 * - stop()             Idle, Initialized
 *
 * If a sound ends because it was manually stopped by the user, prepare() must be run again before start(). If start()
 * is run we will enter Error state, forcing us to go back to reset().
 * If, on the other hand, the sound was stopped by entering PlaybackCompleted, prepare() is not necessary; start() can
 * be run again immediately. Although running prepare() again in this case doesn't break anything.
 *
 * The objective is to always leave the player in a state where start() may be called without any previous setup.
 * This means it should be in any of the states: Prepared, Paused, PlaybackCompleted. It also means that:
 * - On manual stop (state = Stopped), we should immediately call prepare().
 * - When onError has been called (state = Error), we should call reset() > setDataSource() > prepare().
 */

class MediaPlayerWrapper :
    LoggingObject,
    MediaPlayer.OnCompletionListener,
    MediaPlayer.OnErrorListener
{
    enum class State { IDLE, INITIALIZED, PREPARED, STARTED, PAUSED, STOPPED, PLAYBACK_COMPLETED, ERROR, END }

    fun interface OnStateChangeListener {
        fun onStateChange(mp: MediaPlayerWrapper, state: State)
    }

    private val _mp: MediaPlayer = MediaPlayer().also {
        it.setOnCompletionListener(this)
        it.setOnErrorListener(this)
    }
    private val _state = MutableStateFlow(State.IDLE)
    private val _hasPermanentError = MutableStateFlow(false)
    private val _temporaryError = Channel<String>()
    private val _permanentError = Channel<String>()
    private var _onStateChangeListener: OnStateChangeListener? = null
    private var _path: String? = null
    private val _currentPosition: Int?
        get() {
            return if (_state.value in listOf(State.STARTED, State.PAUSED))
                if (_mp.duration > 0)
                    min(((_mp.currentPosition.toDouble() / _mp.duration) * 100).roundToInt(), 100)
                else 0
            else null
        }

    val state: StateFlow<State> = _state
    val hasPermanentError: Flow<Boolean> = _hasPermanentError
    val permanentError: Flow<String> = _permanentError.receiveAsFlow()
    val temporaryError: Flow<String> = _temporaryError.receiveAsFlow()
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

    /**
     * Public wrapper methods, that will try to achieve the following:
     *
     * - Only do MediaPlayer operations that the current state allows.
     * - Always leave MediaPlayer in a state where it is ready to receieve a start() call immediately (except for when
     * it should be trashed altogether, of course).
     * - Differentiate between permanent and temporary errors.
     */

    fun destroy() {
        _mp.release()
        changeState(State.END)
        _onStateChangeListener = null
    }

    suspend fun pause() {
        if (_state.value == State.STARTED) wrapPause()
        renderStartable()
    }

    fun play() {
        /**
         * To run start(), player needs to be in Prepared, Paused, or PlaybackCompleted state.
         * If sound is already playing, just do nothing (if we want it to start over from the beginning, we should
         * call restart()).
         */
        log("play(): _state=${_state.value}, _path=$_path")
        if (_state.value != State.STARTED) {
            renderStartable()
            wrapStart()
        }
    }

    fun restart() {
        /** Meaning: play from the beginning, even if we're already playing. */
        if (_state.value == State.STARTED) _mp.seekTo(0)
        else {
            renderStartable()
            wrapStart()
        }
    }

    fun stop() {
        if (_state.value == State.STARTED) wrapStop()
        else if (_state.value == State.PAUSED) _mp.seekTo(0)
        renderStartable()
    }


    fun setOnStateChangeListener(listener: OnStateChangeListener) {
        _onStateChangeListener = listener
    }

    fun setPath(value: String?) {
        if (value != _path) {
            _path = value
            wrapReset()
            renderStartable()
        }
    }

    fun setVolume(value: Int) {
        log("setVolume(): value=$value, _state=${_state.value}, _path=$_path")
        _mp.setVolume(value.toFloat() / 100, value.toFloat() / 100)
    }

    /** PRIVATE METHODS *****************************************************/

    private fun changeState(state: State) {
        log("changeState(): old=${_state.value}, new=$state, _path=$_path")
        if (state != _state.value) {
            _state.value = state
            _onStateChangeListener?.onStateChange(this, state)
        }
    }

    private fun renderStartable() {
        /**
         * Prep the sound so it's in a state where we can just call start() on it next
         * (i.e. Prepared, Paused, or PlaybackCompleted).
         */
        log("renderStartable(): _state=${_state.value}, _path=$_path")
        if (_state.value == State.ERROR) wrapReset()
        if (_state.value == State.IDLE) wrapSetDataSource(_path)
        if (_state.value in listOf(State.STOPPED, State.INITIALIZED)) wrapPrepare()
    }

    private fun setPermanentError(error: String) {
        _permanentError.trySend(error)
        _hasPermanentError.value = true
    }

    /**
     * Valid states: {Started, Paused, PlaybackCompleted}
     * Invalid states: {Idle, Initialized, Prepared, Stopped, Error}
     *
     * Successful invoke of this method in a valid state transfers the object to the Paused state. Calling this method
     * in an invalid state transfers the object to the Error state.
     *
     * Note that the transition from the Started state to the Paused state and vice versa happens asynchronously in the
     * player engine. It may take some time before the state is updated in calls to isPlaying(), and it can be a number
     * of seconds in the case of streamed content.
     */
    private suspend fun wrapPause() {
        try {
            _mp.pause()
            while (_mp.isPlaying) delay(10)
            changeState(State.PAUSED)
        } catch (e: Exception) {
            _temporaryError.trySend("Error on pause(): $e")
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
    private fun wrapPrepare() {
        log("wrapPrepare(): _state=${_state.value}, _path=$_path")
        try {
            _mp.syncParams.tolerance = 0.1f
            _mp.prepare()
            changeState(State.PREPARED)
        } catch (e: IllegalStateException) {
            _temporaryError.trySend("Error on prepare(): $e")
        } catch (e: Exception) {
            setPermanentError("Error on prepare(): $e")
        }
    }

    private fun wrapReset() {
        log("wrapReset(): _state=${_state.value}, _path=$_path")
        _mp.reset()
        changeState(State.IDLE)
    }

    /**
     * Valid states: {Idle}
     * Invalid states: {Initialized, Prepared, Started, Paused, Stopped, PlaybackCompleted, Error}
     *
     * Successful invoke of this method in a valid state transfers the object to the Initialized state. Calling this
     * method in an invalid state throws an IllegalStateException.
     */
    private fun wrapSetDataSource(path: String?) {
        log("wrapSetDataSource(): path=$path, _state=${_state.value}")
        try {
            _mp.setDataSource(path)
            changeState(State.INITIALIZED)
        }
        catch (e: FileNotFoundException) {
            log("FileNotFoundException in wrapSetDataSource(): e=$e, path=$path, _state=${_state.value}")
            setPermanentError("File not found: ${path?.split("/")?.last()}")
        }
        catch (e: IllegalStateException) {
            log("IllegalStateException in wrapSetDataSource(): e=$e, path=$path, _state=${_state.value}")
            _temporaryError.trySend("Error on setDataSource(): $e")
        }
        catch (e: Exception) {
            log("Exception in wrapSetDataSource(): e=$e, path=$path, _state=${_state.value}")
            setPermanentError("Error on setDataSource(): $e")
        }
    }

    /**
     * Valid states: {Prepared, Started, Paused, PlaybackCompleted}
     * Invalid states: {Idle, Initialized, Stopped, Error}
     *
     * Successful invoke of this method in a valid state transfers the object to the Started state. Calling this method
     * in an invalid state transfers the object to the Error state.
     */
    private fun wrapStart() {
        log("wrapStart(): _state=${_state.value}, _path=$_path")
        try {
            _mp.start()
            changeState(State.STARTED)
        } catch (e: Exception) {
            changeState(State.ERROR)
            log("error on wrapStart(): $e, _state=${_state.value}, _path=$_path")
            _temporaryError.trySend("Error on start(): $e")
        }
    }

    /**
     * Valid states: {Prepared, Started, Stopped, Paused, PlaybackCompleted}
     * Invalid states: {Idle, Initialized, Error}
     *
     * Successful invoke of this method in a valid state transfers the object to the Stopped state. Calling this
     * method in an invalid state transfers the object to the Error state.
     */
    private fun wrapStop() {
        log("wrapStop(): _state=${_state.value}, _path=$_path")
        try {
            _mp.stop()
            changeState(State.STOPPED)
        } catch (e: Exception) {
            _temporaryError.trySend("Error on stop(): $e")
        }
    }


    /** OVERRIDDEN METHODS ***************************************************/

    override fun onCompletion(mp: MediaPlayer?) {
        changeState(State.PLAYBACK_COMPLETED)
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
        log("onError(): what=$what, extra=$extra, _state=${_state.value}, _path=$_path")
        if (_state.value != State.ERROR) _temporaryError.trySend("$whatStr: $extraStr")
        changeState(State.ERROR)
        renderStartable()
        return true
    }
}
