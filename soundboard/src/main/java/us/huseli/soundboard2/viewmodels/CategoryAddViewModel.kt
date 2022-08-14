package us.huseli.soundboard2.viewmodels

import android.util.Log
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import us.huseli.soundboard2.BuildConfig
import us.huseli.soundboard2.data.repositories.CategoryRepository
import javax.inject.Inject

@HiltViewModel
class CategoryAddViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository
) : ViewModel() {
    private val _backgroundColor = MutableStateFlow<Int?>(null)
    private val _name = MutableStateFlow("")

    val backgroundColor: LiveData<Int?> = _backgroundColor.asLiveData()

    val name: LiveData<String>
        get() = _name.asLiveData()

    fun setName(value: String) {
        _name.value = value
    }

    fun setBackgroundColor(value: Int) {
        if (BuildConfig.DEBUG) Log.d(LOG_TAG, "setBackgroundColor(): value=$value")
        _backgroundColor.value = value
    }

    fun reset() = viewModelScope.launch {
        _name.value = ""
        categoryRepository.randomColor.collect { setBackgroundColor(it) }
    }

    fun save() = viewModelScope.launch {
        val backgroundColor = _backgroundColor.value
        val name = _name.value
        if (BuildConfig.DEBUG) Log.d(LOG_TAG, "save(): name=$name, backgroundColor=$backgroundColor")
        if (backgroundColor != null && name != "") categoryRepository.create(name, backgroundColor)
    }

    companion object {
        const val LOG_TAG = "CategoryAddViewModel"
    }
}