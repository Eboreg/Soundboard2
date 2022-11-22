package us.huseli.soundboard2.data.entities

import android.net.Uri
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import us.huseli.soundboard2.helpers.SoundSorting
import java.util.*

@Entity(
    tableName = "Sound",
    foreignKeys = [ForeignKey(
        entity = Category::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.SET_NULL,
        onUpdate = ForeignKey.CASCADE,
    )],
    indices = [Index("categoryId")],
)
open class Sound(
    @PrimaryKey(autoGenerate = true) open val id: Int,
    open val categoryId: Int,
    open val name: String,
    open val uri: Uri,
    open val duration: Long,
    open val checksum: String,
    @IntRange(from = 0, to = 100) open val volume: Int,
    open val added: Date,
    @ColorInt open val backgroundColor: Int,
) {
    class Comparator(private val sorting: SoundSorting) : java.util.Comparator<Sound> {
        override fun compare(o1: Sound?, o2: Sound?): Int {
            val s1 = if (sorting.order == SoundSorting.Order.ASCENDING) o1 else o2
            val s2 = if (sorting.order == SoundSorting.Order.ASCENDING) o2 else o1

            return if (s1 == null && s2 == null) 0
            else if (s1 == null) -1
            else if (s2 == null) 1
            else when (sorting.parameter) {
                SoundSorting.Parameter.NAME -> when {
                    s1.name.lowercase(Locale.getDefault()) > s2.name.lowercase(Locale.getDefault()) -> 1
                    s1.name.equals(s2.name, ignoreCase = true) -> 0
                    else -> -1
                }
                SoundSorting.Parameter.DURATION -> when {
                    s1.duration > s2.duration -> 1
                    s1.duration == s2.duration -> 0
                    else -> -1
                }
                SoundSorting.Parameter.TIME_ADDED -> when {
                    s1.added > s2.added -> 1
                    s1.added == s2.added -> 0
                    else -> -1
                }
            }
        }
    }

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
