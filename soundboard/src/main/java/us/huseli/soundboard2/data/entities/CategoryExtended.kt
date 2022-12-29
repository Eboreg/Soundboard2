package us.huseli.soundboard2.data.entities

import androidx.annotation.ColorInt
import androidx.room.ColumnInfo
import us.huseli.soundboard2.helpers.SoundSorting

data class CategoryExtended(
    @ColumnInfo(name = "categoryId")
    override val id: Int,
    @ColumnInfo(name = "categoryName")
    override val name: String,
    @ColorInt
    override val backgroundColor: Int,
    override val position: Int,
    override val collapsed: Boolean = false,
    override val soundSorting: SoundSorting = SoundSorting(SoundSorting.Parameter.NAME, SoundSorting.Order.ASCENDING),
    val isFirst: Boolean,
    val isLast: Boolean
) : Category(id, name, backgroundColor, position, collapsed, soundSorting) {
    override fun toString() = name
}
