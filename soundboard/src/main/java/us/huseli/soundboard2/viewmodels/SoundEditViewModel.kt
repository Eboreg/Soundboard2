package us.huseli.soundboard2.viewmodels

import android.app.Application
import android.content.Context
import android.graphics.Color
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.R
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.data.repositories.SoundRepository
import us.huseli.soundboard2.data.repositories.StateRepository
import us.huseli.soundboard2.helpers.LoggingObject
import javax.inject.Inject

@HiltViewModel
class SoundEditViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: SoundRepository,
    private val stateRepository: StateRepository,
    private val categoryRepository: CategoryRepository,
    application: Application
) : LoggingObject, BaseSoundEditViewModel(application) {
    override val isUpdate = true

    private val emptyCategoryInternal = Category(-1, context.getString(R.string.not_changed), Color.DKGRAY, -1)

    override fun initialize() {
        super.initialize()

        keepVolume.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            categoriesInternal.value =
                listOf(emptyCategoryInternal) + categoryRepository.categories.stateIn(viewModelScope).value
            val sounds = repository.selectedSounds.stateIn(viewModelScope).value

            val colors = sounds.map { it.backgroundColor }.toSet()
            if (colors.size == 1 && colors.first() != Color.TRANSPARENT) {
                backgroundColor.value = colors.first()
                overrideBackgroundColor.value = true
            } else overrideBackgroundColor.value = false

            val volumes = sounds.map { it.volume }.toSet()
            volume.value = if (volumes.size == 1) volumes.first() else Constants.DEFAULT_VOLUME

            val categoryIds = sounds.map { it.categoryId }.toSet()
            categoryPosition.value =
                if (categoryIds.size == 1) categoriesInternal.value.indexOfFirst { it.id == categoryIds.first() }
                else 0

            soundCountInternal.value = sounds.size
            // multipleInternal.value = sounds.size > 1

            name.value =
                if (sounds.size == 1) sounds[0].name
                else context.getString(R.string.multiple_sounds_selected, sounds.size)

            isReadyInternal.value = true
        }
    }

    override fun save() {
        validate()

        viewModelScope.launch(Dispatchers.IO) {
            val category = categoriesInternal.value[categoryPosition.value]
            val sounds = repository.selectedSounds.stateIn(viewModelScope).value

            val changedSounds = sounds.map { sound ->
                sound.clone(
                    name = if (soundCountInternal.value == 1) name.value else null,
                    volume = if (!keepVolume.value) volume.value else null,
                    categoryId = if (category.id != -1) category.id else null,
                    backgroundColor = if (overrideBackgroundColor.value) backgroundColor.value else Color.TRANSPARENT,
                )
            }.filterIndexed { idx, sound -> !sound.isIdentical(sounds[idx]) }

            if (changedSounds.isNotEmpty()) {
                // At least one sound is changed.
                repository.update(changedSounds)
                stateRepository.push()
            }

            repository.disableSelect()
        }
    }
}