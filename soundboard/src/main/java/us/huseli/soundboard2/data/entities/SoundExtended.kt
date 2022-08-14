package us.huseli.soundboard2.data.entities

import androidx.annotation.ColorInt
import java.util.*

data class SoundExtended(
    override val id: Int,
    override val categoryId: Int?,
    override val name: String,
    override val path: String,
    override val order: Int,
    override val duration: Long,
    override val checksum: String,
    override val volume: Int,
    override val added: Date,
    override val trashed: Boolean,
    @ColorInt val backgroundColor: Int?,
) : Sound(id, categoryId, name, path, order, duration, checksum, volume, added, trashed)
