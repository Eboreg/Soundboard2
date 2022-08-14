package us.huseli.soundboard2.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import us.huseli.soundboard2.data.entities.Sound
import us.huseli.soundboard2.data.entities.SoundExtended
import java.util.*

@Dao
interface SoundDao {
    @Query("SELECT * FROM Sound WHERE checksum IN (:checksums) GROUP BY `checksum`")
    fun flowListByChecksums(checksums: List<String>): Flow<List<Sound>>

    @Query("SELECT id FROM Sound WHERE categoryId = :categoryId AND name LIKE :filterTerm ORDER BY `order`")
    fun flowListFilteredIds(categoryId: Int, filterTerm: String): Flow<List<Int>>

    @Query("SELECT s.*, c.backgroundColor FROM Sound s JOIN Category c ON s.categoryId = c.id WHERE s.id = :soundId")
    fun flowGet(soundId: Int): Flow<SoundExtended?>

    @Query("SELECT * FROM Sound WHERE checksum IN (:checksums) GROUP BY `checksum`")
    suspend fun listByChecksums(checksums: List<String>): List<Sound>

    @Query("SELECT * FROM Sound WHERE categoryId = :categoryId")
    suspend fun listByCategory(categoryId: Int): List<Sound>

    @Query("SELECT COALESCE(MAX(`order`), -1) + 1 FROM Sound WHERE categoryId = :categoryId")
    suspend fun getNextOrder(categoryId: Int): Int

    @Query("""
        INSERT INTO Sound (name, path, duration, checksum, categoryId, `order`, volume, added, trashed)
        VALUES (:name, :path, :duration, :checksum, :categoryId, :order, :volume, :added, :trashed)
    """)
    suspend fun create(
        name: String,
        path: String,
        duration: Long,
        checksum: String,
        volume: Int,
        added: Date,
        trashed: Boolean = false,
        categoryId: Int? = null,
        order: Int? = null,
    )

    @Query("DELETE FROM Sound WHERE categoryId = :categoryId")
    suspend fun deleteByCategory(categoryId: Int)

    @Query("UPDATE Sound SET categoryId = :categoryId, `order` = :order WHERE id = :soundId")
    suspend fun move(soundId: Int, categoryId: Int, order: Int)
}