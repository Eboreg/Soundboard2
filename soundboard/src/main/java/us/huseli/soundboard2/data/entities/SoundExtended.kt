package us.huseli.soundboard2.data.entities

import android.net.Uri
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import java.util.*

data class SoundExtended(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "soundId")
    override val id: Int,
    @ColumnInfo(name = "soundCategoryId")
    override val categoryId: Int,
    @ColumnInfo(name = "soundName")
    override val name: String,
    override val uri: Uri,
    override val duration: Long,
    override val checksum: String,
    @IntRange(from = 0, to = 100)
    override val volume: Int,
    override val added: Date,
    @ColorInt
    @ColumnInfo(name = "soundBackgroundColor")
    override val backgroundColor: Int,
    @ColorInt
    val categoryColor: Int?,
) : Sound(id, categoryId, name, uri, duration, checksum, volume, added, backgroundColor) {
    companion object {
        fun create(sound: Sound, category: Category): SoundExtended = SoundExtended(
            sound.id,
            sound.categoryId,
            sound.name,
            sound.uri,
            sound.duration,
            sound.checksum,
            sound.volume,
            sound.added,
            sound.backgroundColor,
            category.backgroundColor,
        )
    }
}
