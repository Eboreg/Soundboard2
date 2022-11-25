package us.huseli.soundboard2.viewmodels

import android.app.Application
import android.graphics.Color
import androidx.annotation.ColorRes
import androidx.annotation.IntRange
import androidx.lifecycle.*
import kotlinx.coroutines.flow.*
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.R
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.entities.SoundExtended
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.helpers.ValidationError

@Suppress("MemberVisibilityCanBePrivate")
abstract class BaseSoundEditViewModel(application: Application) : LoggingObject, AndroidViewModel(application) {
    abstract val isUpdate: Boolean

    protected val categoriesInternal = MutableStateFlow<List<Category>>(emptyList())
    protected val duplicatesInternal = MutableStateFlow<List<SoundExtended>>(emptyList())
    protected val isReadyInternal = MutableStateFlow(false)
    protected val soundCountInternal = MutableStateFlow(0)

    val addDuplicates = MutableStateFlow(false)
    @ColorRes
    val backgroundColor = MutableStateFlow(Color.TRANSPARENT)
    val categoryPosition = MutableStateFlow(0)
    val keepVolume = MutableStateFlow(false)
    val name = MutableStateFlow("")
    val overrideBackgroundColor = MutableStateFlow(false)
    @IntRange(from = 0, to = 100)
    val volume = MutableStateFlow(Constants.DEFAULT_VOLUME)

    val categories: LiveData<List<Category>> = categoriesInternal.asLiveData()
    val duplicateCount: LiveData<Int> = duplicatesInternal.map { it.size }.asLiveData()
    val hasDuplicates: LiveData<Boolean> = duplicatesInternal.map { it.isNotEmpty() }.asLiveData()
    val isReady: LiveData<Boolean> = isReadyInternal.asLiveData()
    val nameIsEditable: LiveData<Boolean> = soundCountInternal.map { it == 1 }.asLiveData()
    val soundCount: LiveData<Int> = soundCountInternal.asLiveData()

    open val addSoundCount: LiveData<Int> = liveData { emit(0) }
    open val skipSoundCount: LiveData<Int> = liveData { emit(0) }

    open fun initialize() {
        backgroundColor.value = Color.TRANSPARENT
        isReadyInternal.value = false
    }

    abstract fun save()

    fun validate() {
        if (soundCountInternal.value == 1 && name.value.trim().isEmpty())
            throw ValidationError(R.string.name_cannot_be_empty)
    }
}