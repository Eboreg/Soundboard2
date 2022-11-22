package us.huseli.soundboard2.ui.fragments

import android.text.Html
import android.text.TextUtils
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import us.huseli.soundboard2.R
import us.huseli.soundboard2.databinding.FragmentEditSoundsBinding
import us.huseli.soundboard2.viewmodels.SoundAddViewModel

@AndroidEntryPoint
class SoundAddFragment : BaseSoundEditFragment() {
    @StringRes
    override val dialogTitle = R.string.add_sound
    @PluralsRes
    override val dialogTitlePlural = R.plurals.add_sounds
    override val viewModel by activityViewModels<SoundAddViewModel>()

    override fun onBindingCreated(binding: FragmentEditSoundsBinding) {
        super.onBindingCreated(binding)

        viewModel.duplicateData.observe(this) {
            binding.column2.duplicateText.text = Html.fromHtml(
                resources.getQuantityString(
                    R.plurals.sound_already_exists,
                    it.count,
                    it.count,
                    TextUtils.htmlEncode(it.name)
                ),
                Html.FROM_HTML_MODE_LEGACY
            )
        }
    }
}