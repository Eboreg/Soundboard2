package us.huseli.soundboard2.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
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
    open val categoryId: Int?,
    open val name: String,
    open val path: String,
    open val order: Int,
    open val duration: Long,
    open val checksum: String,
    open val volume: Int,
    open val added: Date,
    open val trashed: Boolean,
) {
    override fun equals(other: Any?) = other is Sound && other.id == id

    override fun hashCode() = id

    override fun toString(): String {
        val hashCode = Integer.toHexString(System.identityHashCode(this))
        return "Sound $hashCode <id=$id, name=$name, categoryId=$categoryId>"
    }
}
