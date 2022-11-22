package us.huseli.soundboard2.data.dao

import androidx.annotation.ColorInt
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.entities.CategoryWithSounds
import us.huseli.soundboard2.helpers.SoundSorting

@Dao
interface CategoryDao {
    @Query("SELECT * FROM Category ORDER BY position")
    fun flowList(): Flow<List<Category>>

    @Query("SELECT id FROM Category ORDER BY position")
    fun flowListIds(): Flow<List<Int>>

    @Query("SELECT * FROM Category WHERE id = :categoryId")
    fun flowGet(categoryId: Int): Flow<Category?>

    @Transaction
    @Query("SELECT * FROM Category ORDER BY position")
    fun flowListWithSounds(): Flow<List<CategoryWithSounds>>

    @Query("SELECT COUNT(*) FROM Sound WHERE categoryId = :categoryId")
    suspend fun getSoundCount(categoryId: Int): Int

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM Category")
    suspend fun getNextPosition(): Int

    @Query(
        """
        INSERT INTO Category (name, backgroundColor, position, soundSorting, collapsed)
        VALUES (:name, :backgroundColor, :position, :soundSorting, 0)
        """
    )
    suspend fun create(name: String, @ColorInt backgroundColor: Int, position: Int, soundSorting: SoundSorting)

    @Query("SELECT * FROM Category")
    suspend fun list(): List<Category>

    @Query("SELECT DISTINCT backgroundColor FROM Category")
    suspend fun listUsedColors(): List<Int>

    @Query("UPDATE Category SET collapsed = CASE WHEN collapsed = 0 THEN 1 ELSE 0 END WHERE id = :categoryId")
    suspend fun toggleCollapsed(categoryId: Int)

    @Delete
    suspend fun delete(categories: Collection<Category>)

    @Update
    suspend fun update(categories: Collection<Category>)

    @Insert
    fun insert(categories: Collection<Category>)

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

    @Query("SELECT * FROM Category WHERE id = :categoryId")
    suspend fun get(categoryId: Int): Category
}