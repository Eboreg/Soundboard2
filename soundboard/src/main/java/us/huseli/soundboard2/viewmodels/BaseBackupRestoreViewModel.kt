package us.huseli.soundboard2.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.entities.Sound
import us.huseli.soundboard2.helpers.UriDeserializer
import us.huseli.soundboard2.helpers.UriSerializer

abstract class BaseBackupRestoreViewModel(application: Application) : AndroidViewModel(application) {
    data class DatabaseObjects(val categories: List<Category>, val sounds: List<Sound>)

    abstract val currentFileHeader: Int

    protected var currentJob: Job? = null
    protected val isBackupFinishedInternal = MutableStateFlow(false)
    protected val isRestoreFinishedInternal = MutableStateFlow(false)
    protected val isInProgressInternal = MutableStateFlow(false)
    protected val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Uri::class.java, UriSerializer())
        .registerTypeAdapter(Uri::class.java, UriDeserializer())
        .create()

    val currentFileName = MutableStateFlow<String?>(null)
    val progress = MutableStateFlow(0)
    val error = MutableStateFlow<String?>(null)
    val hasError = error.map { it != null }.asLiveData()
    val restoreFinishedText = MutableStateFlow("")
    val isInProgress = isInProgressInternal.asLiveData()
    val isBackupFinished = isBackupFinishedInternal.asLiveData()
    val isRestoreFinished = isRestoreFinishedInternal.asLiveData()
    val isFinished = combine(isBackupFinishedInternal, isRestoreFinishedInternal) { backupFinished, restoreFinished ->
        backupFinished || restoreFinished
    }.asLiveData()

    fun cancelCurrentJob() {
        currentJob?.cancel()
        reset()
    }

    protected fun reset() {
        currentFileName.value = null
        progress.value = 0
        error.value = null
        isInProgressInternal.value = false
        isBackupFinishedInternal.value = false
        isRestoreFinishedInternal.value = false
    }

    fun setError(value: String) {
        reset()
        error.value = value
    }
}