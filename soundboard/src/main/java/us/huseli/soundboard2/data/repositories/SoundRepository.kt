package us.huseli.soundboard2.data.repositories

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.Functions
import us.huseli.soundboard2.data.SoundFile
import us.huseli.soundboard2.data.dao.SoundDao
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.entities.Sound
import us.huseli.soundboard2.data.entities.SoundExtended
import us.huseli.soundboard2.helpers.LoggingObject
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundRepository @Inject constructor(
    private val soundDao: SoundDao,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : LoggingObject {
    val sounds: Flow<List<Sound>> = soundDao.flowList()
    val allChecksums: Flow<List<String>> = soundDao.flowListAllChecksums()

    fun get(soundId: Int): Flow<SoundExtended?> = soundDao.flowGet(soundId)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun listFiltered(): Flow<List<Sound>> = settingsRepository.soundFilterTerm.flatMapLatest { soundDao.flowListFiltered("%$it%") }

    fun listByIds(soundIds: Collection<Int>): Flow<List<Sound>> = soundDao.flowListByIds(soundIds)

    fun listByChecksums(checksums: List<String>): Flow<List<Sound>> = soundDao.flowListByChecksums(checksums)

    /** If duplicate == null: copy file to local storage. Otherwise: just use same path as duplicate. */
    suspend fun create(soundFile: SoundFile, explicitName: String?, volume: Int, categoryId: Int, duplicate: Sound?) {
        val uri = duplicate?.uri ?: Functions.copyFileToLocal(context, soundFile).toUri()
        val name = explicitName ?: duplicate?.name ?: soundFile.name

        log("create(): soundFile=$soundFile, path=$uri, name=$name, explicitName=$explicitName, volume=$volume, categoryId=$categoryId, duplicate=$duplicate")

        soundDao.create(
            name,
            uri,
            soundFile.duration,
            soundFile.checksum,
            volume,
            Date(),
            categoryId = categoryId,
            order = soundDao.getNextOrder(categoryId)
        )
    }

    suspend fun create(soundFile: SoundFile, categoryId: Int) =
        create(soundFile, null, Constants.DEFAULT_VOLUME, categoryId, null)

    suspend fun delete(sounds: List<Sound>) = soundDao.delete(sounds)

    suspend fun update(sounds: List<Sound>, name: String?, volume: Int?, category: Category?) {
        var nextOrder = category?.let { soundDao.getNextOrder(it.id) }
        val newSounds = sounds.map {
            it.clone(
                name = name,
                volume = volume,
                categoryId = category?.id,
                order = if (it.categoryId != category?.id && nextOrder != null) nextOrder++ else null
            )
        }
        soundDao.update(newSounds)
    }

    /** SOUND SELECTION ******************************************************/

    private val _selectedSoundIds = MutableStateFlow<Set<Int>>(emptySet())
    private val _selectEnabled = MutableStateFlow<Boolean?>(null)

    val lastSelectedId: Flow<Int?> = _selectedSoundIds.map { it.lastOrNull() }
    val selectEnabled: Flow<Boolean> = _selectEnabled.filterNotNull()
    val selectedSoundIds: StateFlow<Set<Int>> = _selectedSoundIds.asStateFlow()

    fun enableSelect() { _selectEnabled.value = true }

    @Suppress("MemberVisibilityCanBePrivate")
    fun disableSelect() {
        _selectEnabled.value = false
        _selectedSoundIds.value = emptySet()
    }

    fun select(soundId: Int) { _selectedSoundIds.value += soundId }

    fun unselect(soundId: Int) {
        _selectedSoundIds.value -= soundId
        if (_selectedSoundIds.value.isEmpty()) disableSelect()
    }
}