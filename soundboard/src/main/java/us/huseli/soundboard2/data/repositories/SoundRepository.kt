package us.huseli.soundboard2.data.repositories

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
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
import us.huseli.soundboard2.helpers.SoundSorting
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
     * Map of category -> sounds, where categories are sorted by Category.order,
     * and sounds are sorted by their respective categories' soundSorting.
     */
    private val _categoriesWithSounds: Flow<Map<Category, List<Sound>>> = categoryDao.flowListWithSounds().map {
        it.toMutableMap().apply {
            replaceAll { category, sounds -> sounds.sortedWith(Sound.Comparator(category.soundSorting)) }
            log("_categoriesWithSounds: it=$it")
        }
    }

    /**
     * Flattened list of sounds with backgroundColor, sorted by their
     * respective categories' soundSorting.
     */
    val allSounds: Flow<List<SoundExtended>> = soundDao.flowMap().map {
        it.toMutableMap().apply {
            replaceAll { soundSorting, sounds -> sounds.sortedWith(Sound.Comparator(soundSorting)) }
        }.values.flatten()
    }

    val _allSounds: Flow<List<SoundExtended>> = _categoriesWithSounds.map {
        it.flatMap { entry -> entry.value.map { sound -> SoundExtended.create(sound, entry.key) } }
            .also { sounds -> log("sounds: $sounds") }
    }

    val allSoundsFiltered: Flow<List<SoundExtended>> =
        combine(settingsRepository.soundFilterTerm, allSounds) { term, sounds ->
            sounds.filter { it.name.contains(term) }
        }

    val allChecksums: Flow<List<String>> = allSounds.map { list -> list.map { it.checksum } }

    fun listByCategoryFiltered(category: Category): Flow<List<SoundExtended>> =
        allSoundsFiltered.map { sounds -> sounds.filter { it.categoryId == category.id } }

    /** If duplicate == null: copy file to local storage. Otherwise: just use same path as duplicate. */
    suspend fun create(soundFile: SoundFile, explicitName: String?, volume: Int, category: Category, duplicate: Sound?) {
        val uri = duplicate?.uri ?: Functions.copyFileToLocal(context, soundFile).toUri()
        val name = explicitName ?: duplicate?.name ?: soundFile.name

        log("""
            create(): name=$name, uri=$uri, soundFile.duration=${soundFile.duration}, 
            soundFile.checksum=${soundFile.checksum}, volume=$volume, Date()=${Date()}, category=$category,
            soundDao.getNextOrder(categoryId)=${soundDao.getNextOrder(category.id)}
        """.trimIndent())

        soundDao.create(
            name,
            uri,
            soundFile.duration,
            soundFile.checksum,
            volume,
            Date(),
            category.id,
            soundDao.getNextOrder(category.id)
        )
    }

    suspend fun create(soundFile: SoundFile, category: Category) =
        create(soundFile, null, Constants.DEFAULT_VOLUME, category, null)

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
    private val _selectedSounds = MutableStateFlow<Set<Sound>>(emptySet())
    private val _selectEnabled = MutableStateFlow<Boolean?>(null)

    val lastSelected: Flow<Sound?> = _selectedSounds.map { it.lastOrNull() }
    val selectEnabled: Flow<Boolean> = _selectEnabled.filterNotNull()
    val selectedSounds: Flow<List<Sound>> = _selectedSounds.map { it.toList() }

    fun enableSelect() { _selectEnabled.value = true }

    @Suppress("MemberVisibilityCanBePrivate")
    fun disableSelect() {
        _selectEnabled.value = false
        _selectedSoundIds.value = emptySet()
        _selectedSounds.value = emptySet()
    }

    fun select(sound: Sound) { _selectedSounds.value += sound }

    fun unselect(sound: Sound) {
        _selectedSounds.value -= sound
        if (_selectedSounds.value.isEmpty()) disableSelect()
    }
}