package us.huseli.soundboard2.ui.fragments

import android.graphics.Color
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import us.huseli.soundboard2.R
import us.huseli.soundboard2.databinding.FragmentEditSoundsBinding
import us.huseli.soundboard2.viewmodels.SoundEditViewModel

@AndroidEntryPoint
class SoundEditFragment : BaseSoundEditFragment() {
    @StringRes
    override val dialogTitle = R.string.edit_sound
    @PluralsRes
    override val dialogTitlePlural = R.plurals.edit_sounds
    override val viewModel by activityViewModels<SoundEditViewModel>()

    override fun onBindingCreated(binding: FragmentEditSoundsBinding) {
        super.onBindingCreated(binding)
        binding.column2.clearColorButton.setOnClickListener { viewModel.backgroundColor.value = Color.TRANSPARENT }
    }
}