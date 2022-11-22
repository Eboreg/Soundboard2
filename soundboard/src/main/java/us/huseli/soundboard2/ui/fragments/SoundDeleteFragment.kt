package us.huseli.soundboard2.ui.fragments

import android.os.Bundle
import android.text.Html
import android.text.TextUtils
import android.view.LayoutInflater
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import us.huseli.soundboard2.R
import us.huseli.soundboard2.databinding.FragmentDeleteSoundsBinding
import us.huseli.soundboard2.viewmodels.SoundDeleteViewModel

class SoundDeleteFragment : BaseDialogFragment<FragmentDeleteSoundsBinding>() {
    private val viewModel by activityViewModels<SoundDeleteViewModel>()
    @StringRes
    override val positiveButtonText = R.string.delete
    @StringRes
    override val negativeButtonText = R.string.cancel

    override fun onCreateBinding(layoutInflater: LayoutInflater, savedInstanceState: Bundle?) =
        FragmentDeleteSoundsBinding.inflate(layoutInflater)

    override fun onDialogCreated(dialog: AlertDialog) {
        viewModel.soundData.observe(this) {
            dialog.setTitle(resources.getQuantityString(R.plurals.delete_sounds, it.count))

            binding.deleteSoundsText.text = Html.fromHtml(
                resources.getQuantityString(
                    R.plurals.delete_sounds_confirm,
                    it.count,
                    it.count,
                    TextUtils.htmlEncode(it.name)
                ),
                Html.FROM_HTML_MODE_LEGACY
            )
        }
    }

    override fun onPositiveButtonClick(): Boolean {
        viewModel.delete()
        return true
    }
}