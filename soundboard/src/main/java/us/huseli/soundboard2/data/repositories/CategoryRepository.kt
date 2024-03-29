package us.huseli.soundboard2.data.repositories

import androidx.annotation.ColorInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import us.huseli.soundboard2.data.dao.CategoryDao
import us.huseli.soundboard2.data.dao.SoundDao
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.entities.CategoryExtended
import us.huseli.soundboard2.helpers.ColorHelper
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.helpers.SoundSorting
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val soundDao: SoundDao,
    private val colorHelper: ColorHelper,
    ioScope: CoroutineScope
) : LoggingObject {
    private val _defaultCategoryCreationMutex = Mutex()

    val categories: SharedFlow<List<CategoryExtended>> =
        categoryDao.flowListExtended().shareIn(ioScope, SharingStarted.Lazily, 1)
    val firstCategory: Flow<Category> = categories.map {
        if (it.isEmpty()) createDefault()
        it.firstOrNull()
    }.filterNotNull()

    suspend fun applyState(categories: Collection<Category>) = categoryDao.applyState(categories)

    suspend fun create(name: CharSequence, @ColorInt backgroundColor: Int, soundSorting: SoundSorting) =
        categoryDao.create(name.toString(), backgroundColor, categoryDao.getNextPosition(), soundSorting)

    suspend fun createDefault() = _defaultCategoryCreationMutex.withLock {
        create(
            "Dëfäult",
            getRandomColor(),
            SoundSorting(SoundSorting.Parameter.NAME, SoundSorting.Order.ASCENDING)
        )
    }

    suspend fun delete(category: Category, moveSoundsTo: Int?) {
        if (moveSoundsTo == null) soundDao.deleteByCategoryId(category.id)
        else soundDao.update(soundDao.listByCategoryId(category.id).map { sound ->
            sound.clone(categoryId = moveSoundsTo)
        })
        categoryDao.delete(listOf(category))
    }

    suspend fun get(categoryId: Int): Category = categoryDao.get(categoryId)

    suspend fun getRandomColor(@ColorInt vararg exclude: Int): Int =
        colorHelper.getRandomColor(exclude = categoryDao.listUsedColors() + exclude.asList())

    suspend fun getSoundCount(categoryId: Int): Int = categoryDao.getSoundCount(categoryId)

    suspend fun list(): List<Category> = categoryDao.list()

    suspend fun toggleCollapsed(categoryId: Int) = categoryDao.toggleCollapsed(categoryId)

    suspend fun update(vararg categories: Category) = categoryDao.update(categories.asList())

}