package us.huseli.soundboard2.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import us.huseli.soundboard2.Enums
import us.huseli.soundboard2.data.repositories.CategoryRepository
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(private val categoryRepository: CategoryRepository) : ViewModel() {
    private val _repressMode = MutableStateFlow(Enums.RepressMode.STOP)

    val categoryIds = categoryRepository.categoryIds.asLiveData()
    val repressMode: LiveData<Enums.RepressMode>
        get() = _repressMode.asLiveData()

    fun setFilterTerm(value: String) {
        categoryRepository.filterTerm.value = value
    }
}