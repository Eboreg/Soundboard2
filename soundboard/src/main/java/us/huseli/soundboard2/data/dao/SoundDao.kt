package us.huseli.soundboard2.data.dao

import android.net.Uri
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import us.huseli.soundboard2.data.entities.Sound
import us.huseli.soundboard2.data.entities.SoundExtended
import us.huseli.soundboard2.helpers.SoundSorting
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

    @Query(
        """
        INSERT INTO Sound (name, uri, duration, checksum, categoryId, `order`, volume, added, trashed)
        VALUES (:name, :uri, :duration, :checksum, :categoryId, :order, :volume, :added, :trashed)
    """
    )
    suspend fun create(
        name: String,
        uri: Uri,
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

    @Transaction
    suspend fun sortWithinCategory(categoryId: Int, soundSorting: SoundSorting) {
        val sounds = listByCategory(categoryId)
        sounds.sortedWith(Sound.Comparator(soundSorting))
            .forEachIndexed { index, sound -> updateOrder(sound.id, index) }
    }

    @Query("UPDATE Sound SET `order` = :order WHERE id = :soundId")
    suspend fun updateOrder(soundId: Int, order: Int)
}