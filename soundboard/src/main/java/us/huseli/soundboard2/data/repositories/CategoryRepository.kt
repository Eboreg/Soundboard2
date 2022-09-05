package us.huseli.soundboard2.data.repositories

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
    val randomColor: Flow<Int> = categoryDao.flowListUsedColors().map { colorHelper.getRandomColor(exclude = it) }

    val firstCategory: Flow<Category> = categories.map {
        if (it.isEmpty()) createDefault()
        it.firstOrNull()
    }.filterNotNull()

    fun getSoundCount(category: Category) = categoryDao.flowGetSoundCount(category.id)

    suspend fun toggleCollapsed(category: Category) = categoryDao.toggleCollapsed(category.id)

    suspend fun create(name: CharSequence, backgroundColor: Int, soundSorting: SoundSorting) =
        categoryDao.create(name.toString(), backgroundColor, categoryDao.getNextOrder(), soundSorting)

    suspend fun delete(category: Category, moveSoundsTo: Int?) {
        val sounds = soundDao.listByCategoryId(category.id)

        if (moveSoundsTo == null) {
            val duplicateUris = soundDao.listByChecksums(sounds.map { it.checksum })
                .filter { it.categoryId != category.id }
                .map { it.uri }
                .toSet()
            val urisToDelete = sounds.map { it.uri }.subtract(duplicateUris)

            context.getDir(Constants.SOUND_DIRNAME, Context.MODE_PRIVATE)
                ?.listFiles()
                ?.forEach { if (urisToDelete.contains(it.toUri())) it.delete() }
            soundDao.deleteByCategoryId(category.id)
        }
        else {
            val nextOrder = soundDao.getNextOrder(moveSoundsTo)
            val newSounds = sounds.mapIndexed { index, sound ->
                sound.clone(categoryId = moveSoundsTo, order = nextOrder + index)
            }
            soundDao.update(newSounds)
        }
        categoryDao.delete(category)
    }

    suspend fun update(category: Category) = categoryDao.update(category)

    suspend fun createDefault() = create(
        "Dëfäult",
        randomColor.first(),
        SoundSorting(SoundSorting.Parameter.CUSTOM, SoundSorting.Order.ASCENDING)
    )
}