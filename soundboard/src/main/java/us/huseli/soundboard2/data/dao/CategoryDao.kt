package us.huseli.soundboard2.data.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.entities.CategoryWithSounds
import us.huseli.soundboard2.helpers.SoundSorting

@Dao
interface CategoryDao {
    @Query("SELECT * FROM Category ORDER BY `order`")
    fun flowList(): Flow<List<Category>>

    @Query("SELECT id FROM Category ORDER BY `order`")
    fun flowListIds(): Flow<List<Int>>

    @Query("SELECT * FROM Category WHERE id = :categoryId")
    fun flowGet(categoryId: Int): Flow<Category?>

    @Query("SELECT DISTINCT backgroundColor FROM Category")
    fun flowListUsedColors(): Flow<List<Int>>

    @Transaction
    @Query("SELECT * FROM Category ORDER BY `order`")
    fun flowListWithSounds(): Flow<List<CategoryWithSounds>>

    @Query("SELECT COUNT(*) FROM Sound WHERE categoryId = :categoryId")
    fun flowGetSoundCount(categoryId: Int): Flow<Int>

    @Query("SELECT COALESCE(MAX(`order`), -1) + 1 FROM Category")
    suspend fun getNextOrder(): Int

    @Query("""
        INSERT INTO Category (name, backgroundColor, `order`, soundSorting)
        VALUES (:name, :backgroundColor, :order, :soundSorting)
    """)
    suspend fun create(name: String, backgroundColor: Int, order: Int, soundSorting: SoundSorting)

    @Query("UPDATE Category SET collapsed = CASE WHEN collapsed = 0 THEN 1 ELSE 0 END WHERE id = :categoryId")
    suspend fun toggleCollapsed(categoryId: Int)

    @Delete
    suspend fun delete(category: Category)

    @Update
    suspend fun update(category: Category)

    @Update
    suspend fun update(categories: List<Category>)
}