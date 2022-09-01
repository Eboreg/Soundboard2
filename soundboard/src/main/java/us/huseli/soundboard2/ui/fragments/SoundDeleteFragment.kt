package us.huseli.soundboard2.ui.fragments

import android.app.Dialog
import android.os.Bundle
import android.text.Html
import android.text.TextUtils
import android.view.View
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import us.huseli.soundboard2.R
import us.huseli.soundboard2.databinding.FragmentDeleteSoundsBinding
import us.huseli.soundboard2.viewmodels.SoundDeleteViewModel

class SoundDeleteFragment : BaseDialogFragment<FragmentDeleteSoundsBinding>() {
    private val viewModel by activityViewModels<SoundDeleteViewModel>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = FragmentDeleteSoundsBinding.inflate(layoutInflater)

        return MaterialAlertDialogBuilder(requireContext())
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.delete() }
            .setNegativeButton(R.string.cancel) { _, _ -> dismiss() }
            .setTitle("")
            .setView(binding.root)
            .create()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.soundData.observe(this) {
            dialog?.setTitle(resources.getQuantityString(R.plurals.delete_sounds, it.count))

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
}