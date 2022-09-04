package us.huseli.soundboard2.data.dao

import android.net.Uri
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import us.huseli.soundboard2.data.entities.Sound
import us.huseli.soundboard2.data.entities.SoundExtended
import java.util.*

@Dao
interface SoundDao {
    @Query("SELECT s.*, c.backgroundColor, c.soundSorting FROM Sound s JOIN Category c ON s.categoryId = c.id")
    fun flowList(): Flow<List<SoundExtended>>

    @Query("SELECT * FROM Sound WHERE checksum IN (:checksums) GROUP BY `checksum`")
    fun flowListByChecksums(checksums: List<String>): Flow<List<Sound>>

    @Query("SELECT * FROM Sound WHERE id IN (:soundIds)")
    fun flowListByIds(soundIds: Collection<Int>): Flow<List<Sound>>

    @Query("SELECT id FROM Sound WHERE categoryId = :categoryId AND name LIKE :filterTerm")
    fun flowListFilteredIdsByCategoryId(categoryId: Int, filterTerm: String): Flow<List<Int>>

    @Query("SELECT * FROM Sound WHERE categoryId = :categoryId AND name LIKE :filterTerm")
    fun flowListFilteredByCategoryId(categoryId: Int, filterTerm: String): Flow<List<Sound>>

    @Query("""
        SELECT s.*, c.backgroundColor, c.soundSorting 
        FROM Sound s JOIN Category c ON s.categoryId = c.id 
        WHERE s.name LIKE :filterTerm ORDER BY c.`order`, s.`order`
    """)
    fun flowListFiltered(filterTerm: String): Flow<List<SoundExtended>>

    @Query("SELECT s.*, c.backgroundColor, c.soundSorting FROM Sound s JOIN Category c ON s.categoryId = c.id WHERE s.id = :soundId")
    fun flowGet(soundId: Int): Flow<SoundExtended?>

    @Query("SELECT checksum FROM Sound")
    fun flowListAllChecksums(): Flow<List<String>>

    @Query("SELECT * FROM Sound WHERE checksum IN (:checksums) GROUP BY `checksum`")
    suspend fun listByChecksums(checksums: List<String>): List<Sound>

    @Query("SELECT * FROM Sound WHERE categoryId = :categoryId")
    suspend fun listByCategory(categoryId: Int): List<Sound>

    @Query("SELECT COALESCE(MAX(`order`), -1) + 1 FROM Sound WHERE categoryId = :categoryId")
    suspend fun getNextOrder(categoryId: Int): Int

    @Query(
        """
        INSERT INTO Sound (name, uri, duration, checksum, volume, added, categoryId, `order`)
        VALUES (:name, :uri, :duration, :checksum, :volume, :added, :categoryId, :order)
    """
    )
    suspend fun create(
        name: String,
        uri: Uri,
        duration: Long,
        checksum: String,
        volume: Int,
        added: Date,
        categoryId: Int,
        order: Int,
    )

    @Delete
    suspend fun delete(sounds: List<Sound>)

    @Query("DELETE FROM Sound WHERE categoryId = :categoryId")
    suspend fun deleteByCategory(categoryId: Int)

    @Update
    suspend fun update(sounds: List<Sound>)
}