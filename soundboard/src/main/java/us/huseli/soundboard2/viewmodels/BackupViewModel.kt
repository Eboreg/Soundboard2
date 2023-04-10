package us.huseli.soundboard2.viewmodels

import android.app.Application
import android.content.Context
import android.text.format.DateFormat
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.R
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.data.repositories.SettingsRepository
import us.huseli.soundboard2.data.repositories.SoundRepository
import java.io.*
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val soundRepository: SoundRepository,
    application: Application
) : BaseBackupRestoreViewModel(application) {
    override val currentFileHeader = R.string.saving_file
    var backupFile: File? = null

    private fun addFileToZip(zipOut: ZipOutputStream, inputStream: InputStream, path: String) {
        val data = ByteArray(Constants.ZIP_BUFFER_SIZE)
        zipOut.putNextEntry(ZipEntry(path))
        BufferedInputStream(inputStream, Constants.ZIP_BUFFER_SIZE).use {
            do {
                val count = it.read(data, 0, Constants.ZIP_BUFFER_SIZE)
                if (count != -1) zipOut.write(data, 0, count)
            } while (count != -1)
        }
        zipOut.closeEntry()
    }

    fun backup(includeSounds: Boolean = false) {
        currentJob = viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            val outDir = File(context.cacheDir, Constants.BACKUP_DIRNAME).apply { mkdirs() }
            val dateString = DateFormat.format("yyyyMMddHHmmss", Date()).toString()
            val outFile = File(outDir, "soundboard-$dateString.zip").apply { deleteOnExit() }
            val soundFiles = context.getDir(Constants.SOUND_DIRNAME, Context.MODE_PRIVATE).listFiles()
            var currentFileIdx = 0
            val totalFileCount = if (includeSounds) 2 + (soundFiles?.size ?: 0) else 2

            reset()
            isInProgressInternal.value = true

            withContext(Dispatchers.IO) {
                try {
                    ZipOutputStream(FileOutputStream(outFile)).use { zipOutputStream ->
                        ByteArrayInputStream(databaseToJson().toByteArray()).use { inputStream ->
                            currentFileName.value = Constants.ZIP_DB_FILENAME
                            progress.value = ((++currentFileIdx / totalFileCount.toDouble()) * 100).roundToInt()
                            addFileToZip(zipOutputStream, inputStream, Constants.ZIP_DB_FILENAME)
                        }
                        ByteArrayInputStream(settingsRepository.dumpJson().toByteArray()).use { inputStream ->
                            currentFileName.value = Constants.ZIP_PREFS_FILENAME
                            progress.value = ((++currentFileIdx / totalFileCount.toDouble()) * 100).roundToInt()
                            addFileToZip(zipOutputStream, inputStream, Constants.ZIP_PREFS_FILENAME)
                        }
                        if (includeSounds) {
                            soundFiles?.forEach { soundFile ->
                                soundFile.inputStream().use { inputStream ->
                                    currentFileName.value = soundFile.name
                                    progress.value = ((++currentFileIdx / totalFileCount.toDouble()) * 100).roundToInt()
                                    addFileToZip(
                                        zipOutputStream,
                                        inputStream,
                                        Constants.ZIP_SOUND_DIRNAME + "/" + soundFile.name
                                    )
                                }
                            }
                        }
                    }
                    backupFile = outFile
                    isBackupFinishedInternal.value = true
                } catch (e: Exception) {
                    setError(e.toString())
                    throw e
                } finally {
                    isInProgressInternal.value = false
                }
            }
        }
    }

    private suspend fun databaseToJson(): String =
        gson.toJson(DatabaseObjects(categoryRepository.list(), soundRepository.list())) ?: "{}"
}