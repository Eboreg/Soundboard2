package us.huseli.soundboard2.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.annotation.PluralsRes
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.Functions
import us.huseli.soundboard2.R
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.data.repositories.SettingsRepository
import us.huseli.soundboard2.data.repositories.SoundRepository
import us.huseli.soundboard2.helpers.LoggingObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.StringWriter
import java.util.zip.ZipFile
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class RestoreViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val soundRepository: SoundRepository,
    application: Application
) : LoggingObject, BaseBackupRestoreViewModel(application) {
    override val currentFileHeader = R.string.restoring_file

    private fun readTextFileFromZip(inputStream: InputStream): String {
        val buffer = ByteArray(Constants.ZIP_BUFFER_SIZE)
        val writer = StringWriter()
        var length: Int
        while (inputStream.read(buffer).also { length = it } > 0) {
            writer.write(buffer.decodeToString(0, length))
        }
        return writer.toString()
    }

    fun restore(zipUri: Uri) {
        currentJob = viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            var databaseObjects: DatabaseObjects? = null
            var settingsJson: String? = null
            var currentFileIdx = 0
            val soundDir = context.getDir(Constants.SOUND_DIRNAME, Context.MODE_PRIVATE)
            val tempSoundDir = File(context.cacheDir, Constants.TEMP_SOUND_DIRNAME).apply { mkdirs() }
            val backupFile = File(
                File(context.cacheDir, Constants.TEMP_DIRNAME).apply { mkdirs() },
                zipUri.lastPathSegment?.substringAfterLast('/') ?: "backup.zip"
            ).apply { deleteOnExit() }

            reset()
            isInProgressInternal.value = true

            try {
                Functions.copyFileToLocal(context, zipUri, backupFile)
                val zipFile = ZipFile(backupFile)
                val totalFileCount = zipFile.size()

                zipFile.entries()?.iterator()?.forEach { zipEntry ->
                    log("zipEntry: $zipEntry")
                    val buffer = ByteArray(Constants.ZIP_BUFFER_SIZE)
                    if (zipEntry?.name == Constants.ZIP_DB_FILENAME) {
                        currentFileName.value = zipEntry.name
                        databaseObjects = gson.fromJson(
                            readTextFileFromZip(zipFile.getInputStream(zipEntry)),
                            DatabaseObjects::class.java
                        )
                    } else if (zipEntry?.name == Constants.ZIP_PREFS_FILENAME) {
                        currentFileName.value = zipEntry.name
                        settingsJson = readTextFileFromZip(zipFile.getInputStream(zipEntry))
                    } else if (
                        zipEntry?.name?.startsWith("${Constants.ZIP_SOUND_DIRNAME}/") == true
                        && !zipEntry.isDirectory
                    ) {
                        val soundFileName = zipEntry.name.substringAfter('/')
                        if (soundFileName.isNotEmpty()) {
                            currentFileName.value = soundFileName
                            val soundFile = File(tempSoundDir, soundFileName)
                            FileOutputStream(soundFile).use { outputStream ->
                                val inputStream = zipFile.getInputStream(zipEntry)
                                var length: Int
                                while (inputStream.read(buffer).also { length = it } > 0) {
                                    outputStream.write(buffer, 0, length)
                                }
                            }
                        }
                    }
                    progress.value = ((++currentFileIdx / totalFileCount.toDouble()) * 100).roundToInt()
                }

                var restoredSoundFiles = 0
                tempSoundDir.listFiles()?.forEach {
                    if (!it.renameTo(File(soundDir, it.name))) throw Exception("Failed to save file: ${it.name}")
                    restoredSoundFiles++
                }
                val settingsRestored = settingsJson?.let {
                    settingsRepository.loadJson(it)
                    true
                } ?: false
                val databaseRestored = databaseObjects?.let {
                    categoryRepository.applyState(it.categories)
                    soundRepository.applyState(it.sounds)
                    true
                } ?: false

                @PluralsRes
                val pluralResId = when {
                    settingsRestored && databaseRestored -> R.plurals.restored_settings_database_sounds
                    settingsRestored -> R.plurals.restored_settings_sounds
                    databaseRestored -> R.plurals.restored_database_sounds
                    else -> R.plurals.restored_sounds
                }
                restoreFinishedText.value = getApplication<Application>().resources.getQuantityString(
                    pluralResId,
                    restoredSoundFiles,
                    restoredSoundFiles
                )
                isRestoreFinishedInternal.value = true
            } catch (e: Exception) {
                setError(e.toString())
            } finally {
                isInProgressInternal.value = false
                tempSoundDir.listFiles()?.forEach { it.delete() }
            }
        }
    }
}