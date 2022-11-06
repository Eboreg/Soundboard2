package us.huseli.soundboard2.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import us.huseli.soundboard2.Constants
import java.io.File

class MediaPlayerTests(context: Context) : LoggingObject {
    enum class State { IDLE, INITIALIZED, PREPARED, STARTED, PAUSED, STOPPED, PLAYBACK_COMPLETED, ERROR, END }

    enum class Func {
        RESET,
        SETDATASOURCE_VALID_PATH,
        SETDATASOURCE_INVALID_PATH,
        PREPARE,
        SEEKTO0,
        START,
        PAUSE,
        STOP,
    }

    data class TestResult(
        val func: Func,
        val stateAfter: State,
        val exception: Exception? = null
    )

    private var mp = createMediaPlayer()
    private var state: State = State.IDLE
    @SuppressLint("SdCardPath")
    private val validPath =
        "/data/user/0/us.huseli.soundboard2.debug/app_sounds/Ed Sanders-0879c09ef1e092c2588838595fb1045b.flac"
    @SuppressLint("SdCardPath")
    private val invalidPath = "/data/user/0/us.huseli.soundboard2.debug/app_sounds/flurrrv.flac"
    private val logFile = File(context.getDir(Constants.SOUND_DIRNAME, Context.MODE_PRIVATE), "mediaplayertests.log")
    private val logLines = mutableListOf<String>()

    init {
        addLog("init: should now be in Idle state")
    }

    private fun createMediaPlayer(): MediaPlayer {
        return MediaPlayer().apply {
            setOnErrorListener { _, what, extra ->
                state = State.ERROR
                addLog("onError: should now be in Error state. what=$what, extra=$extra")
                true
            }
            setOnCompletionListener {
                state = State.PLAYBACK_COMPLETED
                addLog("onCompletion: should now be in PlaybackCompleted state")
            }
            setOnInfoListener { _, what, extra ->
                addLog("onInfo: what=$what, extra=$extra")
                true
            }
        }
    }

    private fun reinitMediaPlayer() {
        mp.release()
        mp = createMediaPlayer()
    }

    private suspend fun runTestFunc(func: Func): TestResult {
        return try {
            runFunc(func, true)
            delay(500)
            TestResult(func, state)
        } catch (e: Exception) {
            delay(500)
            TestResult(func, state, e)
        }
    }

    private fun getLargestLoopLength(chain: List<Func>): Int? {
        var largestLoopLength: Int? = null
        (1 .. chain.size / 2).forEach { loopLength ->
            val last = chain.slice((chain.size - loopLength) .. chain.lastIndex)
            val secondToLast = chain.slice((chain.size - (loopLength * 2)) .. chain.lastIndex - loopLength)
            if (last == secondToLast) largestLoopLength = loopLength
        }
        return largestLoopLength
    }

    private suspend fun buildResultChains(func: Func, maxDepth: Int, parentFuncChain: List<Func> = emptyList()): List<List<TestResult>> {
        val resultChains = mutableListOf<List<TestResult>>()
        val funcChain = parentFuncChain.plus(func)
        addLog("buildResultChains: " + funcChain.joinToString(" -> "))

        if (getLargestLoopLength(funcChain) != null) return resultChains

        val result = runTestFunc(func)
        val resultChain = mutableListOf(result)
        if (result.exception != null || result.stateAfter == State.ERROR || funcChain.size >= maxDepth)
            return listOf(resultChain)

        Func.values().filterNot { it == func }.forEachIndexed { index, childFunc ->
            // index > 0 means we are starting a new chain of functions where
            // they all need to be executed in order from the beginning:
            if (index > 0) {
                reinitMediaPlayer()
                delay(1000)
                funcChain.forEach { runTestFunc(it) }
            }
            buildResultChains(childFunc, maxDepth, funcChain).forEach { childResultChain ->
                resultChains.add(resultChain.plus(childResultChain))
            }
        }

        return resultChains
    }

    fun runChainTests() = runBlocking {
        logLines.clear()
        reinitMediaPlayer()
        delay(1000)
        buildResultChains(Func.RESET, 4).forEach { chain ->
            addLog("****************************************************************************************")
            addLog("* " + chain.map { it.func }.joinToString(" -> "))
            addLog("****************************************************************************************")
            chain.forEach { result ->
                addLog("${result.func}: stateAfter=${result.stateAfter}, exception=${result.exception}")
            }
        }
        logFile.writeText(logLines.joinToString("\n"))
    }

    fun runFunc(func: Func, rethrow: Boolean = false) {
        when (func) {
            Func.RESET -> reset(rethrow)
            Func.SETDATASOURCE_VALID_PATH -> setDataSource(validPath, rethrow)
            Func.SETDATASOURCE_INVALID_PATH -> setDataSource(invalidPath, rethrow)
            Func.PREPARE -> prepare(rethrow)
            Func.SEEKTO0 -> seekTo0(rethrow)
            Func.START -> start(rethrow)
            Func.PAUSE -> pause(rethrow)
            Func.STOP -> stop(rethrow)
        }
    }

    fun runTests() = runBlocking {
        val funcs = Func.values()
        val startStates = State.values().filter { it <= State.STOPPED }
        val testCount = funcs.size * funcs.size * startStates.size
        var idx = 1

        logLines.clear()

        startStates.forEach { startState ->
            funcs.forEach { func1 ->
                funcs.forEach { func2 ->
                    addLog("****************************************************************************************")
                    addLog("* ($idx/$testCount) Running first $func1, then $func2, from start state $startState")
                    addLog("****************************************************************************************")

                    try {
                        // reinitMediaPlayer()
                        mp.reset()
                        delay(200)
                        if (startState >= State.INITIALIZED) {
                            mp.setDataSource(validPath)
                            delay(200)
                        }
                        if (startState >= State.PREPARED) {
                            mp.prepare()
                            delay(200)
                        }
                        if (startState >= State.STARTED) {
                            mp.start()
                            delay(200)
                        }
                        if (startState == State.PAUSED) {
                            mp.pause()
                            delay(200)
                        }
                        if (startState == State.STOPPED) {
                            mp.stop()
                            delay(200)
                        }

                        delay(500)
                        runFunc(func1)
                        runFunc(func2)
                        delay(1000)
                    } catch (e: Exception) {
                        addLog("ERROR IN STATE PREPARATION: $e")
                        delay(1000)
                    }
                    idx++
                }
            }
        }

        logFile.writeText(logLines.joinToString("\n"))
    }

    private fun reset(rethrow: Boolean = false) {
        try {
            mp.reset()
            state = State.IDLE
            addLog("reset: should now be in Idle state")
        } catch (e: Exception) {
            addLog("reset ERROR: $e")
            if (rethrow) throw e
        }
    }

    private fun setDataSource(path: String, rethrow: Boolean = false) {
        try {
            mp.setDataSource(path)
            state = State.INITIALIZED
            addLog("setDataSource: should now be in Initialized state")
        } catch (e: Exception) {
            addLog("setDataSource ERROR: $e")
            if (rethrow) throw e
        }
    }

    private fun prepare(rethrow: Boolean = false) {
        try {
            mp.prepare()
            state = State.PREPARED
            addLog("prepare: should now be in Prepared state")
        } catch (e: Exception) {
            addLog("prepare ERROR: $e")
            if (rethrow) throw e
        }
    }

    private fun seekTo0(rethrow: Boolean) {
        try {
            mp.seekTo(0)
            addLog("seekTo0: is in $state state")
        } catch (e: Exception) {
            addLog("seekTo0 ERROR: $e")
            if (rethrow) throw e
        }
    }

    private fun start(rethrow: Boolean = false) {
        try {
            mp.start()
            state = State.STARTED
            addLog("start: should now be in Started state")
        } catch (e: Exception) {
            addLog("start ERROR: $e")
            if (rethrow) throw e
        }
    }

    private fun pause(rethrow: Boolean = false) {
        try {
            mp.pause()
            state = State.PAUSED
            addLog("pause: should now be in Paused state")
        } catch (e: Exception) {
            addLog("pause ERROR: $e")
            if (rethrow) throw e
        }
    }

    private fun stop(rethrow: Boolean = false) {
        try {
            mp.stop()
            state = State.STOPPED
            addLog("stop: should now be in Stopped state")
        } catch (e: Exception) {
            addLog("stop ERROR: $e")
            if (rethrow) throw e
        }
    }

    fun release(rethrow: Boolean = false) {
        try {
            mp.release()
            state = State.END
            addLog("release: should now be in End state")
        } catch (e: Exception) {
            addLog("release ERROR: $e")
            if (rethrow) throw e
        }
    }

    private fun addLog(msg: String) {
        super.log(msg, Log.INFO)
        logLines.add(msg)
    }
}