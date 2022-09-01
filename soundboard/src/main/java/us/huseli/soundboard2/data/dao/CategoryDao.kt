package us.huseli.soundboard2.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.entities.CategoryDeleteData

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

    @Query("SELECT c.name, COUNT(s.id) as soundCount FROM Category c LEFT JOIN Sound s ON s.categoryId = c.id WHERE c.id = :categoryId")
    fun flowGetCategoryDeleteData(categoryId: Int): Flow<CategoryDeleteData?>

    @Query("SELECT COALESCE(MAX(`order`), -1) + 1 FROM Category")
    suspend fun getNextOrder(): Int

    @Query("""
        INSERT INTO Category (name, backgroundColor, `order`, collapsed)
        VALUES (:name, :backgroundColor, :order, :collapsed)
    """)
    suspend fun create(name: String, backgroundColor: Int, order: Int, collapsed: Boolean = false)

    @Query("UPDATE Category SET collapsed = CASE WHEN collapsed = 0 THEN 1 ELSE 0 END WHERE id = :categoryId")
    suspend fun toggleCollapsed(categoryId: Int)

    @Query("DELETE FROM Category WHERE id = :categoryId")
    suspend fun delete(categoryId: Int)

    @Update
    suspend fun update(category: Category)

    @Update
    suspend fun update(categories: List<Category>)
}