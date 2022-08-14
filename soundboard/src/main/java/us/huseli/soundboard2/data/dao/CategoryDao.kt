package us.huseli.soundboard2.data.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import us.huseli.soundboard2.data.entities.Category

@Dao
interface CategoryDao {
    @Query("SELECT * FROM Category")
    fun flowList(): Flow<List<Category>>

    @Query("SELECT id FROM Category")
    fun flowListIds(): Flow<List<Int>>

    @Query("SELECT * FROM Category WHERE id = :categoryId")
    fun flowGet(categoryId: Int): Flow<Category?>

    @Query("SELECT DISTINCT backgroundColor FROM Category")
    fun flowListUsedColors(): Flow<List<Int>>

    @Query("SELECT COUNT(*) FROM Sound WHERE categoryId = :categoryId AND trashed = 0")
    fun flowGetSoundCount(categoryId: Int): Flow<Int>

    @Query("SELECT COALESCE(MAX(`order`), -1) + 1 FROM Category")
    suspend fun getNextOrder(): Int

    @Query("""
        INSERT INTO Category (name, backgroundColor, `order`, collapsed, autoImportCategory)
        VALUES (:name, :backgroundColor, :order, :collapsed, :autoImportCategory)
    """)
    suspend fun create(name: CharSequence, backgroundColor: Int, order: Int, collapsed: Boolean = false, autoImportCategory: Boolean = false)

    @Query("UPDATE Category SET collapsed = CASE WHEN collapsed = 0 THEN 1 ELSE 0 END WHERE id = :categoryId")
    suspend fun toggleCollapsed(categoryId: Int)

    @Query("UPDATE Category SET backgroundColor = :backgroundColor WHERE id = :categoryId")
    suspend fun setBackgroundColor(categoryId: Int, backgroundColor: Int)

    @Query("DELETE FROM Category WHERE id = :categoryId")
    suspend fun delete(categoryId: Int)

    @Query("UPDATE Category SET name = :name, backgroundColor = :backgroundColor WHERE id = :categoryId")
    suspend fun update(categoryId: Int, name: CharSequence, backgroundColor: Int)

    @Query("UPDATE Category SET name = :name WHERE id = :categoryId")
    suspend fun update(categoryId: Int, name: CharSequence)
}