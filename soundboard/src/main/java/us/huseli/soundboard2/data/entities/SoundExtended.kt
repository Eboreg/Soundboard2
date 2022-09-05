package us.huseli.soundboard2.data.entities

import android.net.Uri
import androidx.annotation.ColorInt
import us.huseli.soundboard2.helpers.SoundSorting
import java.util.*

data class SoundExtended(
    override val id: Int,
    override val categoryId: Int,
    override val name: String,
    override val uri: Uri,
    override val order: Int,
    override val duration: Long,
    override val checksum: String,
    override val volume: Int,
    override val added: Date,
    @ColorInt val backgroundColor: Int,
    // val soundSorting: SoundSorting
) : Sound(id, categoryId, name, uri, order, duration, checksum, volume, added) {
    companion object {
        fun create(sound: Sound, category: Category): SoundExtended = SoundExtended(
            sound.id,
            sound.categoryId,
            sound.name,
            sound.uri,
            sound.order,
            sound.duration,
            sound.checksum,
            sound.volume,
            sound.added,
            category.backgroundColor,
//             category.soundSorting
        )
    }
}
