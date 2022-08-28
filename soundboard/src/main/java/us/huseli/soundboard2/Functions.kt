package us.huseli.soundboard2

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import us.huseli.soundboard2.data.SoundFile
import us.huseli.soundboard2.helpers.MD5
import java.io.File
import java.io.FileOutputStream

object Functions {
    @Suppress("MemberVisibilityCanBePrivate")
    suspend fun extractChecksum(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        return@withContext runCatching {
            (context.contentResolver.openInputStream(uri) ?: run {
                throw Exception("extractChecksum: openInputStream returned null")
            }).use { return@use MD5.calculate(it) }
        }.getOrElse { throw Exception("extractChecksum threw exception", it) }
    }

    suspend fun copyFileToLocal(context: Context, soundFile: SoundFile): File {
        val path = soundFile.uri.lastPathSegment
        val filename = if (path != null) {
            val (basename, ext) = getFilenameAndExtension(path)
            val maxBasenameLength = 128 - (1 + soundFile.checksum.length + (ext?.let { 1 + ext.length } ?: 0))
            if (basename.length > maxBasenameLength) basename.substring(0, maxBasenameLength) else basename +
                    "-${soundFile.checksum}" + (ext?.let { ".$ext" } ?: "")
        } else soundFile.checksum

        return withContext(Dispatchers.IO) {
            return@withContext runCatching {
                val outputFile = File(context.getDir(Constants.SOUND_DIRNAME, Context.MODE_PRIVATE), filename)
                (context.contentResolver.openInputStream(soundFile.uri) ?: run {
                    throw Exception("copyFileToLocal: openInputStream returned null")
                }).use { inputStream ->
                    FileOutputStream(outputFile).use { outputStream ->
                        val buf = ByteArray(1024)
                        var len: Int
                        while (inputStream.read(buf).also { len = it } > 0) outputStream.write(buf, 0, len)
                    }
                }
                return@runCatching outputFile
            }
        }.getOrElse { throw Exception("copyFileToLocal threw exception", it) }
    }

    /** Returns Pair of name: String, duration: Long. */
    private fun _extractMetadata(retriever: MediaMetadataRetriever, defaultName: String): Pair<String, Long> {
        if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.startsWith("audio") != true)
            throw Exception("extractMetadata: Mimetype is not audio/*")
        val duration =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?:
            run { throw Exception("extractMetadata: Could not get duration") }
        val name = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        return Pair(name ?: defaultName, duration)
    }

    /** Tries to grab title from media tag. If that fails, use filename. */
    fun extractMetadata(context: Context, uri: Uri, checksum: String): SoundFile {
        val cursor = context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)

        val defaultName = cursor?.use {
            cursor.moveToFirst()
            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { index ->
                cursor.getString(index).let {
                    if (it.contains(".")) it.substring(0, it.lastIndexOf(".")) else it
                }
            }
        } ?: ""

        val (name, duration) = MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            _extractMetadata(retriever, defaultName)
        }

        return SoundFile(name, uri, duration, checksum)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    suspend fun extractMetadata(context: Context, uri: Uri): SoundFile =
        extractMetadata(context, uri, extractChecksum(context, uri))

    suspend fun extractMetadata(context: Context, uris: List<Uri>): List<SoundFile> =
        uris.map { extractMetadata(context, it) }

    @Suppress("MemberVisibilityCanBePrivate")
    fun getFilenameAndExtension(path: String): Pair<String, String?> {
        val filename = path.split("/").last()
        val parts = filename.reversed().split(".", limit = 2)
        return if (parts.size == 2)
            Pair(parts[1].reversed(), parts[0].reversed())
        else
            Pair(filename, null)
    }
}