package us.huseli.soundboard2.helpers

import android.media.MediaPlayer
import android.media.SyncParams
import android.util.Log
import androidx.annotation.IntRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import us.huseli.soundboard2.BuildConfig
import us.huseli.soundboard2.Constants
import java.io.FileNotFoundException
import java.io.IOException

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
 *
 * Mapping for the MediaPlayer state integers I've been able to find out:
 * 0 = Error
 * 1 = Idle
 * 2 = Initialized
 * 4 = Preparing??
 * 8 = Prepared
 * 16 = Started
 * 32 = Paused
 * 64 = Stopped
 */

@Suppress("BooleanMethodIsAlwaysInverted")
class MediaPlayerWrapper(private val coroutineScope: CoroutineScope) :
    LoggingObject, MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {
    enum class State { IDLE, INITIALIZED, PREPARING, PREPARED, STARTED, PAUSED, STOPPED, PLAYBACK_COMPLETED, ERROR, END }

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
    private val _path = MutableStateFlow<String?>(null)
    @IntRange(from = 0, to = 100)
    private var _volume = Constants.DEFAULT_VOLUME
    private var _initJob: Job? = null
    private var _resetJob: Job? = null

    val state: StateFlow<State> = _state
    val hasPermanentError: Flow<Boolean> = _hasPermanentError
    val permanentError: Flow<String> = _permanentError.receiveAsFlow()
    val temporaryError: Flow<String> = _temporaryError.receiveAsFlow()
    val currentPosition: Int
        get() = if (_state.value !in listOf(State.IDLE, State.PREPARING, State.ERROR)) _mp.currentPosition else 0
    val duration: Int?
        get() = if (_state.value in listOf(
                State.IDLE,
                State.INITIALIZED,
                State.PREPARING,
                State.ERROR
            )
        ) null else _mp.duration
    val durationFlow: Flow<Int> =  // In milliseconds
        _state.mapNotNull {
            if (it in listOf(
                    State.IDLE,
                    State.PREPARING,
                    State.INITIALIZED,
                    State.ERROR
                )
            ) null else _mp.duration
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

    fun pause() {
        if (_state.value == State.STARTED) wrapPause()
        renderStartable()
    }

    fun play() {
        /**
         * To run start(), player needs to be in Prepared, Paused, or PlaybackCompleted state.
         * If sound is already playing, just do nothing (if we want it to start over from the beginning, we should
         * call restart()).
         */
        log("play(): _state=${_state.value}, _path=${_path.value}")
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

    fun scheduleInit() {
        /**
         * If we're already initialized, do nothing.
         * If we don't have a path yet, wait until we do, then initialize.
         * Otherwise, initialize immediately.
         */
        _resetJob?.also {
            it.cancel()
            _resetJob = null
        }
        log("scheduleInit: starting, _state=${_state.value}, _path=${_path.value}")
        if (_state.value == State.IDLE) coroutineScope.launch {
            // This _should_ suspend until path is not null:
            _path.filterNotNull().take(1).collect {
                log("scheduleInit: initializing, _state=${_state.value}, _path=$it")
                renderStartable()
            }
        }
    }

    fun scheduleReset() {
        _initJob?.also {
            it.cancel()
            _initJob = null
        }
        if (_state.value != State.IDLE) coroutineScope.launch {
            while (_mp.isPlaying) delay(10)
            log("scheduleReset: resetting, _state=${_state.value}, _path=${_path.value}")
            wrapReset()
        }
    }

    fun stop() {
        if (_state.value in listOf(State.STARTED, State.PAUSED)) wrapStop()
        renderStartable()
    }


    fun setOnStateChangeListener(listener: OnStateChangeListener) {
        _onStateChangeListener = listener
    }

    fun setPath(value: String?) {
        log("setPath(): value=$value, old value=${_path.value}, has changed=${value != _path.value}, _state=${_state.value}")
        if (value != _path.value) {
            _path.value = value
            if (_state.value != State.IDLE) wrapReset()
        }
    }

    fun setVolume(@IntRange(from = 0, to = 100) value: Int) {
        _volume = value
    }

    /** PRIVATE METHODS *****************************************************/

    private fun changeState(state: State) {
        log("changeState(): old=${_state.value}, new=$state, _path=${_path.value}")
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
        log("renderStartable(): _state=${_state.value}, _path=${_path.value}")
        if (_state.value == State.ERROR) wrapReset()
        if (_state.value == State.IDLE) wrapSetDataSource(_path.value)
        if (_state.value in listOf(State.STOPPED, State.INITIALIZED)) wrapPrepare()
        _mp.setVolume(_volume.toFloat() / 100, _volume.toFloat() / 100)
    }

    private fun setPermanentError(error: String) {
        _permanentError.trySend(error)
        _hasPermanentError.value = true
    }

    private fun setTemporaryError(error: String) {
        if (BuildConfig.DEBUG) _temporaryError.trySend(error)
        log("Temporary error: $error", Log.ERROR)
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
    private fun wrapPause() {
        try {
            _mp.pause()
            coroutineScope.launch {
                while (_mp.isPlaying) delay(10)
                changeState(State.PAUSED)
            }
        } catch (e: Exception) {
            setTemporaryError("Error on pause(): $e")
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
        log("wrapPrepare(): _state=${_state.value}, _path=${_path.value}")
        try {
            _mp.syncParams.tolerance = 1f / 24
            _mp.syncParams.syncSource = SyncParams.SYNC_SOURCE_AUDIO
            changeState(State.PREPARING)
            _mp.prepare()
        } catch (e: IllegalStateException) {
            setTemporaryError("Error on prepare(): $e")
        } catch (e: Exception) {
            setPermanentError("Error on prepare(): $e")
        }
        changeState(State.PREPARED)
    }

    private fun wrapReset() {
        log("wrapReset(): _state=${_state.value}, _path=${_path.value}")
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
        } catch (e: FileNotFoundException) {
            log("FileNotFoundException in wrapSetDataSource(): e=$e, path=$path, _state=${_state.value}", Log.ERROR)
            setPermanentError("File not found: ${path?.split("/")?.last()}")
        } catch (e: IllegalStateException) {
            log("IllegalStateException in wrapSetDataSource(): e=$e, path=$path, _state=${_state.value}", Log.ERROR)
            setTemporaryError("Error on setDataSource(): $e")
        } catch (e: Exception) {
            log("Exception in wrapSetDataSource(): e=$e, path=$path, _state=${_state.value}", Log.ERROR)
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
        log("wrapStart(): _state=${_state.value}, _path=${_path.value}")
        try {
            _mp.start()
            coroutineScope.launch {
                while (!_mp.isPlaying) delay(10)
                changeState(State.STARTED)
            }
        } catch (e: Exception) {
            changeState(State.ERROR)
            setTemporaryError("Error on start(): $e")
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
        log("wrapStop(): _state=${_state.value}, _path=${_path.value}")
        try {
            _mp.stop()
            changeState(State.STOPPED)
        } catch (e: Exception) {
            setTemporaryError("Error on stop(): $e")
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
        log("onError(): what=$what, extra=$extra, _state=${_state.value}, _path=${_path.value}", Log.ERROR)
        if (_state.value != State.ERROR) setTemporaryError("$whatStr: $extraStr")
        changeState(State.ERROR)
        renderStartable()
        return true
    }
}
