package us.huseli.soundboard2.data.dao

import android.net.Uri
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import us.huseli.soundboard2.data.entities.Sound
import us.huseli.soundboard2.data.entities.SoundExtended
import us.huseli.soundboard2.helpers.SoundSorting
import java.util.*

@Dao
interface SoundDao {
    @Query("SELECT s.*, c.backgroundColor, c.soundSorting FROM Sound s JOIN Category c on s.categoryId = c.id")
    fun flowList(): Flow<List<SoundExtended>>

    @Query("""
        SELECT s.*, c.backgroundColor, c.soundSorting 
        FROM Sound s JOIN Category c ON s.categoryId = c.id 
        WHERE s.name LIKE :filterTerm ORDER BY c.`order`, s.`order`
    """)
    fun flowListFiltered(filterTerm: String): Flow<List<SoundExtended>>

    @MapInfo(keyColumn = "soundSorting")
    @Query("SELECT c.soundSorting, s.*, c.backgroundColor FROM Sound s JOIN Category c on s.categoryId = c.id")
    fun flowMap(): Flow<Map<SoundSorting, List<SoundExtended>>>

    @Query("SELECT * FROM Sound WHERE checksum IN (:checksums) GROUP BY `checksum`")
    suspend fun listByChecksums(checksums: List<String>): List<Sound>

    @Query("SELECT * FROM Sound WHERE categoryId = :categoryId")
    suspend fun listByCategoryId(categoryId: Int): List<Sound>

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
    suspend fun deleteByCategoryId(categoryId: Int)

    @Update
    suspend fun update(sounds: List<Sound>)
}