package us.huseli.soundboard2.data.dao

import android.net.Uri
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import us.huseli.soundboard2.data.entities.Sound
import us.huseli.soundboard2.data.entities.SoundExtended
import java.util.*

@Dao
interface SoundDao {
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

    @Query(
        """
        INSERT INTO Sound (soundName, uri, duration, checksum, volume, added, soundCategoryId, soundBackgroundColor)
        VALUES (:name, :uri, :duration, :checksum, :volume, :added, :categoryId, :backgroundColor)
    """
    )
    suspend fun create(
        name: String,
        uri: Uri,
        duration: Long,
        checksum: String,
        @IntRange(from = 0, to = 100) volume: Int,
        added: Date,
        categoryId: Int,
        @ColorInt backgroundColor: Int,
    )

    @Delete
    suspend fun delete(sounds: Collection<Sound>)

    @Query("DELETE FROM Sound WHERE soundCategoryId = :categoryId")
    suspend fun deleteByCategoryId(categoryId: Int)

    @Query(
        """
        SELECT s.*, c.backgroundColor AS categoryColor FROM Sound s JOIN Category c ON s.soundCategoryId = c.categoryId
        ORDER BY c.position, CASE ABS(c.soundSorting)
            WHEN 1 THEN LOWER(s.soundName)
            WHEN 2 THEN s.duration
            WHEN 3 THEN s.added
            ELSE NULL
        END
        """
    )
    fun flowList(): Flow<List<SoundExtended>>

    @Insert
    suspend fun insert(sounds: Collection<Sound>)

    @Query("SELECT * FROM Sound")
    suspend fun list(): List<Sound>

    @Query("SELECT * FROM Sound WHERE soundCategoryId = :categoryId")
    suspend fun listByCategoryId(categoryId: Int): List<Sound>

    @Update
    suspend fun update(sounds: Collection<Sound>)
}