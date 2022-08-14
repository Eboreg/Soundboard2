package us.huseli.soundboard2.viewmodels

import android.util.Log
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import us.huseli.soundboard2.BuildConfig
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.repositories.CategoryRepository
import javax.inject.Inject

@HiltViewModel
class CategoryDeleteViewModel @Inject constructor(
    private val repository: CategoryRepository
) : ViewModel() {
    private val _categoryId = MutableLiveData<Int>()

    val name: LiveData<String?> = _categoryId.switchMap { categoryId ->
        repository.getCategory(categoryId).map { it?.name }.asLiveData()
    }

    val categoryId: LiveData<Int>
        get() = _categoryId

    val categories: LiveData<List<Category>> = _categoryId.switchMap { categoryId ->
        repository.categories.map { list -> list.filter { it.id != categoryId } }.asLiveData()
    }

    val soundCount: LiveData<Int> = _categoryId.switchMap { categoryId ->
        repository.getSoundCount(categoryId).asLiveData()
    }

    val isLastCategory: LiveData<Boolean> = categories.map { it.isEmpty() }

    val showSoundAction: LiveData<Boolean> = soundCount.switchMap { soundCount ->
        isLastCategory.map { !it && soundCount > 0 }
    }

    fun setCategoryId(value: Int) {
        _categoryId.value = value
    }

    fun delete(categoryId: Int, moveSoundsTo: Int?) = viewModelScope.launch {
        if (BuildConfig.DEBUG) Log.d(LOG_TAG, "delete(): categoryId=$categoryId, moveSoundsTo=$moveSoundsTo")
        repository.delete(categoryId, moveSoundsTo)
    }

    companion object {
        const val LOG_TAG = "CategoryDeleteViewModel"
    }
}