package us.huseli.soundboard2.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.data.repositories.StateRepository
import us.huseli.soundboard2.helpers.LoggingObject
import javax.inject.Inject

@HiltViewModel
class CategoryDeleteViewModel @Inject constructor(
    private val repository: CategoryRepository,
    private val stateRepository: StateRepository
) : LoggingObject, ViewModel() {
    private var categoryIdInternal: Int? = null

    private val isSaveEnabledInternal = MutableStateFlow(false)
    private val nameInternal = MutableStateFlow("")
    private val otherCategoriesInternal = MutableStateFlow<List<Category>>(emptyList())
    private val soundCountInternal = MutableStateFlow(0)
    private val isLastCategoryInternal = MutableStateFlow(false)
    private val showSoundActionInternal = MutableStateFlow(false)

    val newCategoryPosition = MutableStateFlow(0)
    val soundActionMove = MutableStateFlow(true)
    val soundActionDelete = MutableStateFlow(false)

    val name: LiveData<String> = nameInternal.asLiveData()
    val otherCategories: LiveData<List<Category>> = otherCategoriesInternal.asLiveData()
    val soundCount: LiveData<Int> = soundCountInternal.asLiveData()
    val isLastCategory: LiveData<Boolean> = isLastCategoryInternal.asLiveData()
    val showSoundAction: LiveData<Boolean> = showSoundActionInternal.asLiveData()
    val isSaveEnabled: LiveData<Boolean> = isSaveEnabledInternal.asLiveData()

    private suspend fun getCategories(categoryId: Int): Pair<Category, List<Category>> =
        Pair(repository.get(categoryId), repository.list().filter { it.id != categoryId })

    fun initialize(categoryId: Int) {
        categoryIdInternal = categoryId
        isSaveEnabledInternal.value = false
        newCategoryPosition.value = 0
        soundActionMove.value = true
        soundActionDelete.value = false

        viewModelScope.launch(Dispatchers.IO) {
            val (category, otherCategories) = getCategories(categoryId)
            val soundCount = repository.getSoundCount(categoryId)
            val isLastCategory = otherCategories.isEmpty()

            nameInternal.value = category.name
            otherCategoriesInternal.value = otherCategories
            soundCountInternal.value = soundCount
            isLastCategoryInternal.value = isLastCategory
            showSoundActionInternal.value = soundCount > 0 && !isLastCategory
            isSaveEnabledInternal.value = true
        }
    }

    fun delete() {
        categoryIdInternal?.let { categoryId ->
            viewModelScope.launch(Dispatchers.IO) {
                val (category, otherCategories) = getCategories(categoryId)
                val newCategoryId = if (soundActionMove.value) otherCategories[newCategoryPosition.value].id else null
                repository.delete(category, newCategoryId)
                stateRepository.push()
            }
        }
    }
}