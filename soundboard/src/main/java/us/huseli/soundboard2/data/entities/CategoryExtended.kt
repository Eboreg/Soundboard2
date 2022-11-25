package us.huseli.soundboard2.data.entities

import us.huseli.soundboard2.helpers.SoundSorting

data class CategoryExtended(
    override val id: Int,
    override val name: String,
    override val backgroundColor: Int,
    override val position: Int,
    override val collapsed: Boolean = false,
    override val soundSorting: SoundSorting = SoundSorting(SoundSorting.Parameter.NAME, SoundSorting.Order.ASCENDING),
    val isFirst: Boolean,
    val isLast: Boolean
) : Category(id, name, backgroundColor, position, collapsed, soundSorting)
