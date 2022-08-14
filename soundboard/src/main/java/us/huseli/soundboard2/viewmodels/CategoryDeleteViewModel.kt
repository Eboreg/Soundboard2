package us.huseli.soundboard2.viewmodels

import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.helpers.LoggingObject
import javax.inject.Inject

@HiltViewModel
class CategoryDeleteViewModel @Inject constructor(
    private val repository: CategoryRepository
) : LoggingObject, ViewModel() {
    private val categoryId = MutableLiveData<Int>()

    val name: LiveData<String?> = categoryId.switchMap { categoryId ->
        repository.getCategory(categoryId).map { it?.name }.asLiveData()
    }

    val categories: LiveData<List<Category>> = categoryId.switchMap { categoryId ->
        repository.categories.map { list -> list.filter { it.id != categoryId } }.asLiveData()
    }

    val soundCount: LiveData<Int> = categoryId.switchMap { categoryId ->
        repository.getSoundCount(categoryId).asLiveData()
    }

    val isLastCategory: LiveData<Boolean> = categories.map { it.isEmpty() }

    val showSoundAction: LiveData<Boolean> = soundCount.switchMap { soundCount ->
        isLastCategory.map { !it && soundCount > 0 }
    }

    fun setCategoryId(value: Int) {
        categoryId.value = value
    }

    fun delete(moveSoundsTo: Int?) = viewModelScope.launch {
        categoryId.value?.let { categoryId ->
            log("delete(): categoryId=$categoryId, moveSoundsTo=$moveSoundsTo")
            repository.delete(categoryId, moveSoundsTo)
        }
    }
}