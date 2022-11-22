package us.huseli.soundboard2.data.repositories

import android.content.Context
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
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
    private val allSoundsOrderedInternal: Flow<List<SoundExtended>> = categoryDao.flowListWithSounds().map { list ->
        val repr = list.map { Pair(it.category.name, it.sounds.map { sound -> sound.name }) }
        log("allSoundsOrdered: categoryWithSounds=$repr")
        list.flatMap { (category, sounds) ->
            sounds
                .map { sound -> SoundExtended.create(sound, category) }
                .sortedWith(Sound.Comparator(category.soundSorting))
        }
    }

    private val filteredSoundsOrderedInternal: Flow<List<SoundExtended>> =
        combine(settingsRepository.soundFilterTerm, allSoundsOrderedInternal) { term, sounds ->
            sounds.filter { it.name.lowercase().contains(term.lowercase()) }
        }

    val allSounds: Flow<List<SoundExtended>> = soundDao.flowList()
    val allSoundIds: Flow<List<Int>> = soundDao.flowListIds()
    val allChecksums: Flow<Set<String>> = allSounds.map { list -> list.map { it.checksum }.toSet() }
    val filteredSoundIdsOrdered: Flow<List<Int>> = filteredSoundsOrderedInternal.map { list -> list.map { it.id } }

    fun get(soundId: Int): Flow<SoundExtended?> = soundDao.flowGet(soundId)

    suspend fun list() = soundDao.list()

    fun listIdsByCategoryIdFiltered(categoryId: Int): Flow<List<Int>> =
        filteredSoundsOrderedInternal.map { list -> list.filter { it.categoryId == categoryId }.map { it.id } }

    /** If duplicate == null: copy file to local storage. Otherwise: just use same path as duplicate. */
    suspend fun create(
        soundFile: SoundFile,
        explicitName: String?,
        @IntRange(from = 0, to = 100) volume: Int,
        @ColorInt backgroundColor: Int?,
        category: Category,
        duplicate: Sound?
    ) {
        val uri = duplicate?.uri ?: Functions.copyFileToLocal(context, soundFile).toUri()
        val name = explicitName ?: duplicate?.name ?: soundFile.name

        soundDao.create(
            name = name,
            uri = uri,
            duration = soundFile.duration,
            checksum = soundFile.checksum,
            volume = volume,
            added = Date(),
            categoryId = category.id,
            backgroundColor = backgroundColor ?: Color.TRANSPARENT,
        )
    }

    suspend fun create(soundFile: SoundFile, category: Category) =
        create(soundFile, null, Constants.DEFAULT_VOLUME, null, category, null)

    suspend fun delete(sounds: Collection<Sound>) = soundDao.delete(sounds)

    suspend fun update(sounds: Collection<Sound>) = soundDao.update(sounds)

    /** SOUND SELECTION ******************************************************/

    private val selectedSoundIdsInternal = MutableStateFlow<Set<Int>>(emptySet())
    private val isSelectEnabledInternal = MutableStateFlow<Boolean?>(null)

    val lastSelectedId: Flow<Int?> = selectedSoundIdsInternal.map { it.lastOrNull() }
    val isSelectEnabled: Flow<Boolean> = isSelectEnabledInternal.filterNotNull()
    val selectedSoundIds: Flow<List<Int>> = selectedSoundIdsInternal.map { it.toList() }
    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedSounds: Flow<List<Sound>> = selectedSoundIds.flatMapLatest { soundDao.flowListByIds(it) }

    fun enableSelect() {
        isSelectEnabledInternal.value = true
    }

    fun disableSelect() {
        isSelectEnabledInternal.value = false
        selectedSoundIdsInternal.value = emptySet()
    }

    fun select(soundId: Int) {
        selectedSoundIdsInternal.value += soundId
    }

    fun unselect(soundId: Int) {
        selectedSoundIdsInternal.value -= soundId
        if (selectedSoundIdsInternal.value.isEmpty()) disableSelect()
    }
}