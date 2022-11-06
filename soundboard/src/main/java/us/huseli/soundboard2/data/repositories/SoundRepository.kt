package us.huseli.soundboard2.data.repositories

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.Functions
import us.huseli.soundboard2.data.SoundFile
import us.huseli.soundboard2.data.dao.CategoryDao
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
    categoryDao: CategoryDao,
    settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : LoggingObject {
    /**
     * Flattened list of extended Sounds, ordered by their respective
     * categories' soundSorting.
     */
    private val _allSoundsOrdered: Flow<List<SoundExtended>> = categoryDao.flowListWithSounds().map { list ->
        val repr = list.map { Pair(it.category.name, it.sounds.map { sound -> sound.name }) }
        log("allSoundsOrdered: categoryWithSounds=$repr")
        list.flatMap { categoryWithSounds ->
            categoryWithSounds.sounds
                .map { sound -> SoundExtended.create(sound, categoryWithSounds.category) }
                .sortedWith(Sound.Comparator(categoryWithSounds.category.soundSorting))
        }
    }

    private val _filteredSoundsOrdered: Flow<List<SoundExtended>> =
        combine(settingsRepository.soundFilterTerm, _allSoundsOrdered) { term, sounds ->
            sounds.filter { it.name.lowercase().contains(term.lowercase()) }
        }

    val allSounds: Flow<List<SoundExtended>> = soundDao.flowList()
    val allSoundIds: Flow<List<Int>> = soundDao.flowListIds()
    val allChecksums: Flow<List<String>> = allSounds.map { list -> list.map { it.checksum } }
    val filteredSoundIdsOrdered: Flow<List<Int>> = _filteredSoundsOrdered.map { list -> list.map { it.id } }

    fun get(soundId: Int): Flow<SoundExtended?> = soundDao.flowGet(soundId)

    fun listIdsByCategoryIdFiltered(categoryId: Int): Flow<List<Int>> =
        _filteredSoundsOrdered.map { list -> list.filter { it.categoryId == categoryId }.map { it.id } }

    /** If duplicate == null: copy file to local storage. Otherwise: just use same path as duplicate. */
    suspend fun create(soundFile: SoundFile, explicitName: String?, volume: Int, category: Category, duplicate: Sound?) {
        val uri = duplicate?.uri ?: Functions.copyFileToLocal(context, soundFile).toUri()
        val name = explicitName ?: duplicate?.name ?: soundFile.name

        log("""
            create(): name=$name, uri=$uri, soundFile.duration=${soundFile.duration}, 
            soundFile.checksum=${soundFile.checksum}, volume=$volume, Date()=${Date()}, category=$category
        """.trimIndent())

        soundDao.create(
            name,
            uri,
            soundFile.duration,
            soundFile.checksum,
            volume,
            Date(),
            category.id,
        )
    }

    suspend fun create(soundFile: SoundFile, category: Category) =
        create(soundFile, null, Constants.DEFAULT_VOLUME, category, null)

    suspend fun delete(sounds: Collection<Sound>) = soundDao.delete(sounds)

    suspend fun delete(vararg sounds: Sound) = soundDao.delete(sounds.asList())

    suspend fun update(sounds: Collection<Sound>) = soundDao.update(sounds)

    /** SOUND SELECTION ******************************************************/

    private val _selectedSoundIds = MutableStateFlow<Set<Int>>(emptySet())
    private val _selectEnabled = MutableStateFlow<Boolean?>(null)

    val lastSelectedId: Flow<Int?> = _selectedSoundIds.map { it.lastOrNull() }
    val selectEnabled: Flow<Boolean> = _selectEnabled.filterNotNull()
    val selectedSoundIds: Flow<List<Int>> = _selectedSoundIds.map { it.toList() }
    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedSounds: Flow<List<Sound>> = selectedSoundIds.flatMapLatest { soundDao.flowListByIds(it) }

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