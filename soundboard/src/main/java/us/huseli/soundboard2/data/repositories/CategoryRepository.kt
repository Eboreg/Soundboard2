package us.huseli.soundboard2.data.repositories

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.data.dao.CategoryDao
import us.huseli.soundboard2.data.dao.SoundDao
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.entities.Sound
import us.huseli.soundboard2.helpers.ColorHelper
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.helpers.SoundSorting
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val soundDao: SoundDao,
    private val settingsRepository: SettingsRepository,
    colorHelper: ColorHelper,
    @ApplicationContext private val context: Context
) : LoggingObject {
    val categories: Flow<List<Category>> = categoryDao.flowList()
    // val categoryIds: Flow<List<Int>> = categoryDao.flowListIds()
    val randomColor: Flow<Int> = categoryDao.flowListUsedColors().map { colorHelper.getRandomColor(exclude = it) }

    val firstCategory: Flow<Category> = categories.map {
        if (it.isEmpty()) createDefault()
        it.firstOrNull()
    }.filterNotNull()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun listSoundIdsFiltered(category: Category): Flow<List<Int>> = settingsRepository.soundFilterTerm.flatMapLatest { filterTerm ->
        soundDao.flowListFilteredByCategoryId(category.id, "%$filterTerm%").map { sounds ->
            sounds.sortedWith(Sound.Comparator(category.soundSorting)).map { it.id }
        }
    }

    fun get(categoryId: Int): Flow<Category?> = categoryDao.flowGet(categoryId)

    fun getDeleteData(categoryId: Int) = categoryDao.flowGetCategoryDeleteData(categoryId)

    suspend fun toggleCollapsed(categoryId: Int) = categoryDao.toggleCollapsed(categoryId)

    suspend fun create(name: CharSequence, backgroundColor: Int) =
        categoryDao.create(name.toString(), backgroundColor, categoryDao.getNextOrder())

    suspend fun delete(categoryId: Int, moveSoundsTo: Int?) {
        val sounds = soundDao.listByCategory(categoryId)

        if (moveSoundsTo == null) {
            val duplicateUris = soundDao.listByChecksums(sounds.map { it.checksum })
                .filter { it.categoryId != categoryId }
                .map { it.uri }
                .toSet()
            val urisToDelete = sounds.map { it.uri }.subtract(duplicateUris)

            context.getDir(Constants.SOUND_DIRNAME, Context.MODE_PRIVATE)
                ?.listFiles()
                ?.forEach { if (urisToDelete.contains(it.toUri())) it.delete() }
            soundDao.deleteByCategory(categoryId)
        }
        else {
            val nextOrder = soundDao.getNextOrder(moveSoundsTo)
            val newSounds = sounds.mapIndexed { index, sound ->
                sound.clone(categoryId = moveSoundsTo, order = nextOrder + index)
            }
            soundDao.update(newSounds)
        }
        categoryDao.delete(categoryId)
    }

    suspend fun update(category: Category) = categoryDao.update(category)

    suspend fun sortSounds(categoryId: Int, soundSorting: SoundSorting) {
        val sounds = soundDao.listByCategory(categoryId)
            .sortedWith(Sound.Comparator(soundSorting))
            .mapIndexed { index, sound -> sound.clone(order = index) }
        soundDao.update(sounds)
    }

    suspend fun createDefault() = create("Dëfäult", randomColor.first())
}