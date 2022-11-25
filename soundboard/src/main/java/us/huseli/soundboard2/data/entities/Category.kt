package us.huseli.soundboard2.data.entities

import androidx.annotation.ColorInt
import androidx.room.Entity
import androidx.room.PrimaryKey
import us.huseli.soundboard2.helpers.SoundSorting

@Entity(tableName = "Category")
open class Category(
    @PrimaryKey(autoGenerate = true) open val id: Int,
    open val name: String,
    @ColorInt open val backgroundColor: Int,
    open val position: Int,
    open val collapsed: Boolean = false,
    open val soundSorting: SoundSorting = SoundSorting(SoundSorting.Parameter.NAME, SoundSorting.Order.ASCENDING)
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