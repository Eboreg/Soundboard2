package us.huseli.soundboard2.data.entities

import androidx.annotation.ColorInt
import androidx.room.Entity
import androidx.room.PrimaryKey
import us.huseli.soundboard2.helpers.SoundSorting

@Entity(tableName = "Category")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val name: String,
    @ColorInt val backgroundColor: Int,
    val position: Int,
    val collapsed: Boolean = false,
    val soundSorting: SoundSorting = SoundSorting(SoundSorting.Parameter.NAME, SoundSorting.Order.ASCENDING)
) {
    fun clone(
        name: CharSequence? = null,
        @ColorInt backgroundColor: Int? = null,
        position: Int? = null,
        collapsed: Boolean? = null,
        soundSorting: SoundSorting? = null,
    ) = Category(
        id,
        name?.toString() ?: this.name,
        backgroundColor ?: this.backgroundColor,
        position ?: this.position,
        collapsed ?: this.collapsed,
        soundSorting ?: this.soundSorting
    )

    fun isIdenticalTo(other: Category) =
        other.id == id &&
        other.name == name &&
        other.backgroundColor == backgroundColor &&
        other.position == position &&
        other.collapsed == collapsed &&
        other.soundSorting == soundSorting

    override fun toString() = name
    override fun equals(other: Any?) = other is Category && other.id == id
    override fun hashCode() = id
}