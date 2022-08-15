package us.huseli.soundboard2.data.repositories

import android.content.Context
import androidx.annotation.ColorInt
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.data.dao.CategoryDao
import us.huseli.soundboard2.data.dao.SoundDao
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.helpers.ColorHelper
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.helpers.SoundSorting
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val soundDao: SoundDao,
    colorHelper: ColorHelper,
    @ApplicationContext private val context: Context
) : LoggingObject {
    val categories: Flow<List<Category>> = categoryDao.flowList()

    val categoryIds: Flow<List<Int>> = categoryDao.flowListIds()

    val filterTerm = MutableStateFlow("")

    val randomColor: Flow<Int> = categoryDao.flowListUsedColors().map { colorHelper.getRandomColor(exclude = it) }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getSoundIds(categoryId: Int): Flow<List<Int>> =
        filterTerm.flatMapLatest { soundDao.flowListFilteredIds(categoryId, "%$it%") }

    fun getCategory(categoryId: Int): Flow<Category?> = categoryDao.flowGet(categoryId)

    fun getSoundCount(categoryId: Int): Flow<Int> = categoryDao.flowGetSoundCount(categoryId)

    suspend fun setBackgroundColor(categoryId: Int, @ColorInt color: Int) {
        categoryDao.setBackgroundColor(categoryId, color)
    }

    suspend fun toggleCategoryCollapsed(categoryId: Int) = categoryDao.toggleCollapsed(categoryId)

    suspend fun create(name: CharSequence, backgroundColor: Int) =
        categoryDao.create(name.toString(), backgroundColor, categoryDao.getNextOrder())

    suspend fun delete(categoryId: Int, moveSoundsTo: Int?) {
        val sounds = soundDao.listByCategory(categoryId)

        if (moveSoundsTo == null) {
            val duplicatePaths = soundDao.listByChecksums(sounds.map { it.checksum })
                .filter { it.categoryId != categoryId }
                .map { it.path }
                .toSet()
            val pathsToDelete = sounds.map { it.path }.subtract(duplicatePaths)

            context.getDir(Constants.SOUND_DIRNAME, Context.MODE_PRIVATE)
                ?.listFiles()
                ?.forEach { if (pathsToDelete.contains(it.path)) it.delete() }
            soundDao.deleteByCategory(categoryId)
        }
        else {
            val nextOrder = soundDao.getNextOrder(moveSoundsTo)
            sounds.forEachIndexed { idx, sound ->
                log("delete(): categoryId=$categoryId, moveSoundsTo=$moveSoundsTo, idx=$idx, sound=$sound, nextOrder=$nextOrder")
                soundDao.move(sound.id, moveSoundsTo, nextOrder + idx)
            }
        }
        categoryDao.delete(categoryId)
    }

    suspend fun update(
        categoryId: Int,
        name: CharSequence,
        backgroundColor: Int?,
        soundSorting: SoundSorting
    ) {
        if (backgroundColor != null) categoryDao.update(categoryId, name.toString(), backgroundColor)
        else categoryDao.update(categoryId, name.toString())

        if (soundSorting.parameter != SoundSorting.Parameter.UNDEFINED) {
            soundDao.sortWithinCategory(categoryId, soundSorting)
        }
    }
}