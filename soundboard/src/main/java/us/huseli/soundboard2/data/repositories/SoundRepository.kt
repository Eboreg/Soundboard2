package us.huseli.soundboard2.data.repositories

import android.content.Context
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.Functions
import us.huseli.soundboard2.data.SoundFile
import us.huseli.soundboard2.data.dao.CategoryDao
import us.huseli.soundboard2.data.dao.SoundDao
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.entities.CategoryExtended
import us.huseli.soundboard2.data.entities.Sound
import us.huseli.soundboard2.data.entities.SoundExtended
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.helpers.SoundSorting
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundRepository @Inject constructor(
    private val soundDao: SoundDao,
    categoryDao: CategoryDao,
    settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context,
    ioScope: CoroutineScope
) : LoggingObject {
    private val _stopAllSignal = MutableSharedFlow<Boolean>()

    private val _categorySoundMultimap: Flow<Map<CategoryExtended, List<SoundExtended>>> =
        combine(categoryDao.flowListExtended(), soundDao.flowList()) { categories, sounds ->
            categories.associateWith { category ->
                when (category.soundSorting.order) {
                    SoundSorting.Order.ASCENDING -> sounds.filter { it.categoryId == category.id }
                    SoundSorting.Order.DESCENDING -> sounds.filter { it.categoryId == category.id }.reversed()
                }
            }
        }.shareIn(ioScope, SharingStarted.Lazily, 1)

    val categorySoundMultimapVisible: Flow<Map<CategoryExtended, List<SoundExtended>>> =
        combine(settingsRepository.soundFilterTerm, _categorySoundMultimap) { term, multimap ->
            multimap.mapValues { (category, sounds) ->
                if (category.collapsed) emptyList()
                else if (term.isNotBlank()) sounds.filter { sound -> sound.name.lowercase().contains(term.lowercase()) }
                else sounds
            }
        }

    /**
     * Flattened list of extended Sounds, ordered by:
     * 1. category.position
     * 2. category.soundSorting
     */
    val allSounds: Flow<List<SoundExtended>> = _categorySoundMultimap.map { multimap ->
        multimap.flatMap { it.value }
    }.shareIn(ioScope, SharingStarted.Lazily, 1)

    /**
     * Sounds ordered as above, filtered by Category.collapsed and current
     * soundFilterTerm.
     */
    val visibleSounds: Flow<List<SoundExtended>> = categorySoundMultimapVisible.map { multimap ->
        multimap.flatMap { it.value }
    }

    val allChecksums: Flow<Set<String>> = allSounds.map { list -> list.map { it.checksum }.toSet() }

    val stopAllSignal: SharedFlow<Boolean> = _stopAllSignal.asSharedFlow()

    suspend fun list(): List<Sound> = soundDao.list()

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

    suspend fun stopAll() {
        log("stopAll()")
        _stopAllSignal.emit(true)
    }

    /** SOUND SELECTION ******************************************************/

    private val selectedSoundsInternal = MutableStateFlow<Set<Sound>>(emptySet())
    private val isSelectEnabledInternal = MutableStateFlow(false)

    val lastSelectedSound: Flow<Sound?> = selectedSoundsInternal.map { it.lastOrNull() }
    val isSelectEnabled: Flow<Boolean> = isSelectEnabledInternal.filterNotNull()
    val selectedSounds: Flow<List<Sound>> = selectedSoundsInternal.map { it.toList() }

    fun enableSelect() {
        isSelectEnabledInternal.value = true
    }

    fun disableSelect() {
        isSelectEnabledInternal.value = false
        selectedSoundsInternal.value = emptySet()
    }

    fun select(sound: Sound) {
        selectedSoundsInternal.value += sound
    }

    fun unselect(sound: Sound) {
        selectedSoundsInternal.value -= sound
    }
}