package us.huseli.soundboard2.data.repositories

import android.content.Context
import androidx.core.net.toUri
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

    suspend fun create(soundFile: SoundFile, explicitName: String?, volume: Int, categoryId: Int, duplicate: Sound?) {
        /** If duplicate == null: copy file to local storage. Otherwise: just use same path as duplicate. */
        val uri = duplicate?.uri ?: Functions.copyFileToLocal(context, soundFile.uri, soundFile.checksum).toUri()
        val name = explicitName ?: duplicate?.name ?: soundFile.name

        log("create(): soundFile=$soundFile, path=$uri, name=$name, explicitName=$explicitName, volume=$volume, categoryId=$categoryId, duplicate=$duplicate")

        soundDao.create(
            name,
            uri,
            soundFile.duration,
            soundFile.checksum,
            volume,
            Date(),
            categoryId = categoryId,
            order = soundDao.getNextOrder(categoryId)
        )
    }
}