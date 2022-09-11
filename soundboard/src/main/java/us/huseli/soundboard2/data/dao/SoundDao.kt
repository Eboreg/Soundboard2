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
    @Query("SELECT s.*, c.backgroundColor FROM Sound s JOIN Category c ON s.categoryId = c.id WHERE s.id = :soundId")
    fun flowGet(soundId: Int): Flow<SoundExtended>

    @Query("SELECT s.*, c.backgroundColor FROM Sound s JOIN Category c ON s.categoryId = c.id")
    fun flowList(): Flow<List<SoundExtended>>

    @Query("SELECT * FROM Sound WHERE id IN (:soundIds)")
    fun flowListByIds(soundIds: List<Int>): Flow<List<Sound>>

    @Query("SELECT id FROM Sound")
    fun flowListIds(): Flow<List<Int>>

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