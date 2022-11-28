package us.huseli.soundboard2.data.dao

import androidx.annotation.ColorInt
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.entities.CategoryExtended
import us.huseli.soundboard2.data.entities.CategoryWithSounds
import us.huseli.soundboard2.helpers.SoundSorting

@Dao
interface CategoryDao {
    @Transaction
    suspend fun applyState(categories: Collection<Category>) {
        val dbCategories = list().toSet()
        // Insert those that are in `categories` but not in DB:
        insert(categories.subtract(dbCategories))
        // Delete those that are in DB but not in `categories`:
        delete(dbCategories.subtract(categories.toSet()))
        // Update those that are in both:
        update(categories.intersect(dbCategories))
    }

    @Query(
        """
        INSERT INTO Category (name, backgroundColor, position, soundSorting, collapsed)
        VALUES (:name, :backgroundColor, :position, :soundSorting, 0)
        """
    )
    suspend fun create(name: String, @ColorInt backgroundColor: Int, position: Int, soundSorting: SoundSorting)

    @Delete
    suspend fun delete(categories: Collection<Category>)

    @Query(
        """
            SELECT *,
                CASE WHEN (SELECT MIN(position) FROM Category) = position THEN 1 ELSE 0 END AS isFirst,
                CASE WHEN (SELECT MAX(position) FROM Category) = position THEN 1 ELSE 0 END AS isLast
            FROM Category WHERE id = :categoryId
        """
    )
    fun flowGetExtended(categoryId: Int): Flow<CategoryExtended?>

    @Query("SELECT * FROM Category ORDER BY position")
    fun flowList(): Flow<List<Category>>

    @Query("SELECT id FROM Category ORDER BY position")
    fun flowListIds(): Flow<List<Int>>

    @Transaction
    @Query("SELECT * FROM Category ORDER BY position")
    fun flowListWithSounds(): Flow<List<CategoryWithSounds>>

    @Query("SELECT * FROM Category WHERE id = :categoryId")
    suspend fun get(categoryId: Int): Category

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM Category")
    suspend fun getNextPosition(): Int

    @Query("SELECT COUNT(*) FROM Sound WHERE categoryId = :categoryId")
    suspend fun getSoundCount(categoryId: Int): Int

    @Insert
    fun insert(categories: Collection<Category>)

    @Query("SELECT * FROM Category ORDER BY position")
    suspend fun list(): List<Category>

    @Query("SELECT DISTINCT backgroundColor FROM Category")
    suspend fun listUsedColors(): List<Int>

    @Query("UPDATE Category SET collapsed = CASE WHEN collapsed = 0 THEN 1 ELSE 0 END WHERE id = :categoryId")
    suspend fun toggleCollapsed(categoryId: Int)

    @Update
    suspend fun update(categories: Collection<Category>)
}