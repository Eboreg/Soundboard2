package us.huseli.soundboard2.data.entities

import android.net.Uri
import androidx.annotation.ColorInt
import java.util.*

data class SoundExtended(
    override val id: Int,
    override val categoryId: Int,
    override val name: String,
    override val uri: Uri,
    override val duration: Long,
    override val checksum: String,
    override val volume: Int,
    override val added: Date,
    @ColorInt val backgroundColor: Int,
) : Sound(id, categoryId, name, uri, duration, checksum, volume, added) {
    companion object {
        fun create(sound: Sound, category: Category): SoundExtended = SoundExtended(
            sound.id,
            sound.categoryId,
            sound.name,
            sound.uri,
            sound.duration,
            sound.checksum,
            sound.volume,
            sound.added,
            category.backgroundColor,
        )
    }
}
