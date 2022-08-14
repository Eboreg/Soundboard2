package us.huseli.soundboard2.data

import android.net.Uri

/** A not yet saved sound file. */
data class SoundFile(
    val name: String,
    val uri: Uri,
    val duration: Long,
    val checksum: String,
)
