package us.huseli.soundboard2.data.dao

import android.net.Uri
import androidx.room.*
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
    fun flowListByIds(soundIds: Collection<Int>): Flow<List<Sound>>

    @Query("SELECT id FROM Sound")
    fun flowListIds(): Flow<List<Int>>

    @Query("SELECT * FROM Sound")
    suspend fun list(): List<Sound>

    @Query("SELECT * FROM Sound WHERE checksum IN (:checksums) GROUP BY `checksum`")
    suspend fun listByChecksums(checksums: Collection<String>): List<Sound>

    @Query("SELECT * FROM Sound WHERE categoryId = :categoryId")
    suspend fun listByCategoryId(categoryId: Int): List<Sound>

    @Query(
        """
        INSERT INTO Sound (name, uri, duration, checksum, volume, added, categoryId)
        VALUES (:name, :uri, :duration, :checksum, :volume, :added, :categoryId)
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
    )

    @Delete
    suspend fun delete(sounds: Collection<Sound>)

    @Query("DELETE FROM Sound WHERE categoryId = :categoryId")
    suspend fun deleteByCategoryId(categoryId: Int)

    @Update
    suspend fun update(sounds: Collection<Sound>)

    @Insert
    suspend fun insert(sounds: Collection<Sound>)

    @Transaction
    suspend fun applyState(sounds: Collection<Sound>) {
        val dbSounds = list().toSet()
        // Insert those that are in `categories` but not in DB:
        insert(sounds.subtract(dbSounds))
        // Delete those that are in DB but not in `categories`:
        delete(dbSounds.subtract(sounds.toSet()))
        // Update those that are in both:
        update(sounds.intersect(dbSounds))
    }
}