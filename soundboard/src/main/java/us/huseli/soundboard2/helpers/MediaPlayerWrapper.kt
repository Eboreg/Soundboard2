package us.huseli.soundboard2.helpers

import android.media.MediaPlayer
import android.media.SyncParams
import android.util.Log
import androidx.annotation.IntRange
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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
class MediaPlayerWrapper : LoggingObject, MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {
    enum class State { NONE, IDLE, INITIALIZED, PREPARING, PREPARED, STARTED, PAUSED, STOPPED, PLAYBACK_COMPLETED, ERROR }

    fun interface StateListener {
        fun onStateChange(mp: MediaPlayerWrapper, state: State)
    }

    private var _mp: MediaPlayer? = null
    private var _state = State.NONE
    private var _hasPermanentError = false
    private var _stateListener: StateListener? = null
    private var _playerEventListener: PlayerEventListener? = null
    private var _path: String? = null
    @IntRange(from = 0, to = 100)
    private var _volume = 100

    val hasPermanentError: Boolean
        get() = _hasPermanentError
    private val mediaPlayer: MediaPlayer
        get() = _mp ?: createMediaPlayer().also { _mp = it }
    val state: State
        get() = _state

    /**
     * Public wrapper methods, that will try to achieve the following:
     *
     * - Only do MediaPlayer operations that the current state allows.
     * - Always leave MediaPlayer in a state where it is ready to receieve a start() call immediately (except for when
     * it should be trashed altogether, of course).
     * - Differentiate between permanent and temporary errors.
     */

    fun destroy() {
        wrapRelease()
        _stateListener = null
        _playerEventListener = null
    }

    fun initialize() {
        _mp = _mp ?: createMediaPlayer()
    }

    fun pause() {
        if (_state == State.STARTED) wrapPause()
        renderStartable()
    }

    fun play() {
        /**
         * To run start(), player needs to be in Prepared, Paused, or PlaybackCompleted state.
         * If sound is already playing, just do nothing (if we want it to start over from the beginning, we should
         * call restart()).
         */
        log("play(): _state=${_state}, _path=${_path}")
        if (_state != State.STARTED) {
            renderStartable()
            wrapStart()
        }
    }

    fun release() {
        wrapRelease()
    }

    fun restart() {
        /** Meaning: play from the beginning, even if we're already playing. */
        if (_state == State.STARTED) {
            mediaPlayer.seekTo(0)
            _playerEventListener?.onPlaybackStarted()
        } else {
            renderStartable()
            wrapStart()
        }
    }

    fun setPlaybackEventListener(listener: PlayerEventListener) {
        _playerEventListener = listener
    }

    fun setStateListener(listener: StateListener) {
        _stateListener = listener
    }

    fun setPath(value: String?) {
        log("setPath(): value=$value, old value=${_path}, has changed=${value != _path}, _state=${_state}")
        if (value != _path) {
            _path = value
            if (_state !in listOf(State.IDLE, State.NONE)) wrapReset()
        }
    }

    fun setVolume(@IntRange(from = 0, to = 100) value: Int) {
        _volume = value
    }

    fun stop() {
        if (_state in listOf(State.STARTED, State.PAUSED)) wrapStop()
        wrapReset()
    }

    /** PRIVATE METHODS ******************************************************/

    private fun changeState(state: State) {
        log("changeState(): old=${_state}, new=$state, _path=${_path}")
        if (state != _state) {
            _state = state
            when (state) {
                State.STARTED ->
                    _playerEventListener?.onPlaybackStarted()
                State.PAUSED ->
                    _playerEventListener?.onPlaybackPaused()
                in listOf(
                    State.STOPPED,
                    State.ERROR,
                    State.PLAYBACK_COMPLETED,
                    State.NONE
                ) -> _playerEventListener?.onPlaybackStopped()
                else -> {}
            }

            _stateListener?.onStateChange(this, _state)
        }
    }

    private fun createMediaPlayer() = MediaPlayer().also {
        it.setOnCompletionListener(this)
        it.setOnErrorListener(this)
        it.setSurface(null)
        changeState(State.IDLE)
        // runBlocking { DebugData.addMediaPlayer(it) }
    }

    private fun renderStartable() {
        /**
         * Prep the sound so it's in a state where we can just call start() on it next
         * (i.e. Prepared, Paused, or PlaybackCompleted).
         */
        log("renderStartable(): _state=${_state}, _path=${_path}")
        if (_state in listOf(State.ERROR, State.NONE)) wrapReset()
        if (_state == State.IDLE) wrapSetDataSource(_path)
        if (_state in listOf(State.STOPPED, State.INITIALIZED)) wrapPrepare()
        wrapSetVolume(_volume)
    }

    private fun setPermanentError(error: String) {
        _hasPermanentError = true
        _playerEventListener?.onPermanentError(error)
    }

    private fun setTemporaryError(error: String) {
        _playerEventListener?.onTemporaryError(error)
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
            mediaPlayer.pause()
            runBlocking {
                while (mediaPlayer.isPlaying) delay(10)
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
        log("wrapPrepare(): _state=${_state}, _path=${_path}")
        try {
            mediaPlayer.syncParams.tolerance = 1f / 24
            mediaPlayer.syncParams.syncSource = SyncParams.SYNC_SOURCE_AUDIO
            changeState(State.PREPARING)
            mediaPlayer.prepare()
            changeState(State.PREPARED)
        } catch (e: IllegalStateException) {
            setTemporaryError("Error on prepare(): $e")
        } catch (e: Exception) {
            setPermanentError("Error on prepare(): $e")
        }
    }

    private fun wrapRelease() {
        @Suppress("SimpleRedundantLet")
        _mp?.let {
            it.release()
            // runBlocking { DebugData.removeMediaPlayer(it) }
        }
        _mp = null
        changeState(State.NONE)
    }

    private fun wrapReset() {
        log("wrapReset(): _state=${_state}, _path=${_path}")
        mediaPlayer.reset()
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
        log("wrapSetDataSource(): path=$path, _state=${_state}")
        try {
            mediaPlayer.setDataSource(path)
            changeState(State.INITIALIZED)
        } catch (e: FileNotFoundException) {
            log("FileNotFoundException in wrapSetDataSource(): e=$e, path=$path, _state=${_state}", Log.ERROR)
            setPermanentError("File not found: ${path?.split("/")?.last()}")
        } catch (e: IllegalStateException) {
            log("IllegalStateException in wrapSetDataSource(): e=$e, path=$path, _state=${_state}", Log.ERROR)
            setTemporaryError("Error on wrapSetDataSource(): $e")
        } catch (e: Exception) {
            log("Exception in wrapSetDataSource(): e=$e, path=$path, _state=${_state}", Log.ERROR)
            setPermanentError("Error on wrapSetDataSource(): $e")
        }
    }

    private fun wrapSetVolume(@IntRange(from = 0, to = 100) volume: Int) {
        log("wrapSetVolume(): volume=$volume, _path=${_path}, _state=$_state")
        try {
            mediaPlayer.setVolume(volume.toFloat() / 100, volume.toFloat() / 100)
        } catch (e: Exception) {
            setTemporaryError("Error on wrapSetVolume(): $e")
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
        log("wrapStart(): _state=${_state}, _path=${_path}")
        try {
            mediaPlayer.start()
            runBlocking {
                while (!mediaPlayer.isPlaying) delay(10)
                changeState(State.STARTED)
            }
        } catch (e: Exception) {
            changeState(State.ERROR)
            setTemporaryError("Error on wrapStart(): $e")
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
        log("wrapStop(): _state=${_state}, _path=${_path}")
        try {
            mediaPlayer.stop()
            changeState(State.STOPPED)
        } catch (e: Exception) {
            changeState(State.ERROR)
            setTemporaryError("Error on wrapStop(): $e")
        }
    }

    /** OVERRIDDEN METHODS ***************************************************/

    override fun onCompletion(mp: MediaPlayer?) {
        changeState(State.PLAYBACK_COMPLETED)
        wrapReset()
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
        log("onError(): what=$what, extra=$extra, _state=${_state}, _path=${_path}", Log.ERROR)
        setTemporaryError("$whatStr: $extraStr")
        changeState(State.ERROR)
        wrapReset()
        return true
    }

    override fun toString(): String {
        return super.toString() + ": state=$_state"
    }
}
