package us.huseli.soundboard2.data.repositories

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import us.huseli.soundboard2.Functions
import us.huseli.soundboard2.data.SoundFile
import us.huseli.soundboard2.data.dao.SoundDao
import us.huseli.soundboard2.data.entities.Sound
import us.huseli.soundboard2.data.entities.SoundExtended
import us.huseli.soundboard2.helpers.LoggingObject
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundRepository @Inject constructor(private val soundDao: SoundDao, @ApplicationContext private val context: Context) :
    LoggingObject {

    fun getSound(soundId: Int): Flow<SoundExtended?> = soundDao.flowGet(soundId)

    fun listByChecksums(checksums: List<String>): Flow<List<Sound>> = soundDao.flowListByChecksums(checksums)

    suspend fun create(uri: Uri, volume: Int, categoryId: Int) {
        val soundFile = Functions.extractMetadata(context, uri)
        val file = Functions.copyFileToLocal(context, uri, soundFile.checksum)
        val order = soundDao.getNextOrder(categoryId)

        soundDao.create(
            soundFile.name,
            file.path,
            soundFile.duration,
            soundFile.checksum,
            volume,
            Date(),
            categoryId = categoryId,
            order = order
        )
    }

    /** If duplicate == null: copy file to local storage. Otherwise: just use same path as duplicate. */
    suspend fun create(soundFile: SoundFile, explicitName: String?, volume: Int, categoryId: Int, duplicate: Sound?) {
        val path = duplicate?.path ?: Functions.copyFileToLocal(context, soundFile.uri, soundFile.checksum).path
        val name = explicitName ?: duplicate?.name ?: soundFile.name

        log("create(): soundFile=$soundFile, path=$path, name=$name, explicitName=$explicitName, volume=$volume, categoryId=$categoryId, duplicate=$duplicate")

        soundDao.create(
            name,
            path,
            soundFile.duration,
            soundFile.checksum,
            volume,
            Date(),
            categoryId = categoryId,
            order = soundDao.getNextOrder(categoryId)
        )
    }
}