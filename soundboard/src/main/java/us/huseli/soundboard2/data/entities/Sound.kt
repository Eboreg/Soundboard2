package us.huseli.soundboard2.data.entities

import android.net.Uri
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import androidx.room.*
import java.util.*

@Entity(
    tableName = "Sound",
    foreignKeys = [ForeignKey(
        entity = Category::class,
        parentColumns = ["categoryId"],
        childColumns = ["soundCategoryId"],
        onDelete = ForeignKey.SET_NULL,
        onUpdate = ForeignKey.CASCADE,
    )],
    indices = [Index("soundCategoryId")],
)
open class Sound(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "soundId")
    open val id: Int,
    @ColumnInfo(name = "soundCategoryId")
    open val categoryId: Int,
    @ColumnInfo(name = "soundName")
    open val name: String,
    open val uri: Uri,
    open val duration: Long,
    open val checksum: String,
    @IntRange(from = 0, to = 100)
    open val volume: Int,
    open val added: Date,
    @ColorInt
    @ColumnInfo(name = "soundBackgroundColor")
    open val backgroundColor: Int,
) {
    fun clone(
        categoryId: Int? = null,
        name: String? = null,
        uri: Uri? = null,
        duration: Long? = null,
        checksum: String? = null,
        @IntRange(from = 0, to = 100) volume: Int? = null,
        added: Date? = null,
        @ColorInt backgroundColor: Int? = null,
    ) = Sound(
        id,
        categoryId ?: this.categoryId,
        name ?: this.name,
        uri ?: this.uri,
        duration ?: this.duration,
        checksum ?: this.checksum,
        volume ?: this.volume,
        added ?: this.added,
        backgroundColor ?: this.backgroundColor,
    )

    fun isIdentical(other: Sound) =
        other.id == id &&
        other.categoryId == categoryId &&
        other.name == name &&
        other.uri == uri &&
        other.duration == duration &&
        other.checksum == checksum &&
        other.volume == volume &&
        other.added == added &&
        other.backgroundColor == backgroundColor

    override fun equals(other: Any?) = other is Sound && other.id == id
    override fun hashCode() = id
}
