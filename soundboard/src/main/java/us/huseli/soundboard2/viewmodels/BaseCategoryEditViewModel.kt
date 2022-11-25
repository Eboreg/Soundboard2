package us.huseli.soundboard2.viewmodels

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import us.huseli.soundboard2.R
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.helpers.SoundSorting
import us.huseli.soundboard2.helpers.ValidationError

abstract class BaseCategoryEditViewModel(protected val categoryRepository: CategoryRepository) : ViewModel() {
    protected val isReadyInternal = MutableStateFlow(false)
    protected val soundSortingInternal: SoundSorting
        get() = SoundSorting(
            SoundSorting.Parameter.values()[sortParameterPosition.value],
            if (sortOrderAscending.value) SoundSorting.Order.ASCENDING else SoundSorting.Order.DESCENDING
        )

    val name = MutableStateFlow("")
    val sortParameterPosition = MutableStateFlow(0)
    val sortOrderAscending = MutableStateFlow(true)
    val sortOrderDescending = MutableStateFlow(false)

    @ColorInt
    val backgroundColor = MutableStateFlow(Color.TRANSPARENT)
    val isReady: LiveData<Boolean> = isReadyInternal.asLiveData()

    protected suspend fun setRandomBackgroundColorInternal() {
        backgroundColor.value = categoryRepository.getRandomColor(backgroundColor.value)
    }

    abstract fun save()

    fun setRandomBackgroundColor() = viewModelScope.launch {
        setRandomBackgroundColorInternal()
    }

    open fun validate() {
        if (name.value.trim().isEmpty()) throw ValidationError(R.string.name_cannot_be_empty)
    }
}