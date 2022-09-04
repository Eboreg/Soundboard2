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
    val order: Int,
    val collapsed: Boolean = false,
    val soundSorting: SoundSorting = SoundSorting(SoundSorting.Parameter.NAME, SoundSorting.Order.ASCENDING)
) {
    fun clone(
        name: CharSequence? = null,
        backgroundColor: Int? = null,
        order: Int? = null,
        collapsed: Boolean? = null,
        soundSorting: SoundSorting? = null,
    ) = Category(
        this.id,
        name?.toString() ?: this.name,
        backgroundColor ?: this.backgroundColor,
        order ?: this.order,
        collapsed ?: this.collapsed,
        soundSorting ?: this.soundSorting
    )

    override fun equals(other: Any?) = other is Category && other.id == id
    override fun hashCode() = id
}