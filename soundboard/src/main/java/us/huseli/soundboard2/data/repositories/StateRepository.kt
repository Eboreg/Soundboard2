package us.huseli.soundboard2.data.repositories

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.data.dao.CategoryDao
import us.huseli.soundboard2.data.dao.SoundDao
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.entities.Sound
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val categoryDao: CategoryDao,
    private val soundDao: SoundDao
) {
    data class State(val sounds: List<Sound>, val categories: List<Category>)

    private val _states = mutableListOf<State>()
    private val _currentPos = MutableStateFlow(-1)

    val allPaths = _states.flatMap { (sounds, _) -> sounds.mapNotNull { it.uri.path } }.toSet()
    val isUndoPossible: Flow<Boolean> = _currentPos.map { it > 0 }
    val isRedoPossible: Flow<Boolean> = _currentPos.map { it + 1 < _states.size }

    private suspend fun apply(pos: Int): Boolean {
        val state = _states.getOrNull(pos)
        return if (state != null) {
            categoryDao.applyState(state.categories)
            soundDao.applyState(state.sounds)
            _currentPos.value = pos
            true
        } else false
    }

    private fun deleteSoundFiles(removedState: State, nextState: State) {
        val files = context.getDir(Constants.SOUND_DIRNAME, Context.MODE_PRIVATE).listFiles()
        val removedUris = removedState.sounds.map { it.uri }.subtract(nextState.sounds.map { it.uri }.toSet())
        files?.forEach { file -> if (removedUris.contains(file.toUri())) file.delete() }
    }

    suspend fun push() {
        /** On push of new state, all states after _currentPos become unusable and are scrapped: */
        if (_currentPos.value + 1 < _states.size) {
            _states.removeAll(_states.subList(_currentPos.value + 1, _states.size).toSet())
            _currentPos.value = _states.size - 1
        }
        _states.add(State(soundDao.list(), categoryDao.list()))
        _currentPos.value++
        /** If max undo states is reached, delete the first one: */
        if (_states.size > Constants.MAX_UNDO_STATES) _states.removeFirstOrNull()?.also { removedState ->
            _currentPos.value--
            _states.firstOrNull()?.also { nextState -> deleteSoundFiles(removedState, nextState) }
        }
    }

    suspend fun redo() = apply(_currentPos.value + 1)

    suspend fun replaceCurrent() {
        if (_currentPos.value < 0) push()
        else _states[_currentPos.value] = State(soundDao.list(), categoryDao.list())
    }

    suspend fun undo() = apply(_currentPos.value - 1)
}