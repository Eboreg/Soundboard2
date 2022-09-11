package us.huseli.soundboard2.ui.fragments

import android.app.Dialog
import android.os.Bundle
import android.text.Html
import android.text.TextUtils
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import us.huseli.soundboard2.R
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.databinding.FragmentAddSoundsBinding
import us.huseli.soundboard2.ui.CategorySpinnerAdapter
import us.huseli.soundboard2.viewmodels.SoundAddViewModel

class SoundAddFragment : BaseDialogFragment<FragmentAddSoundsBinding>() {
    private val viewModel by activityViewModels<SoundAddViewModel>()
    private var multiple = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        viewModel.reset()
        binding = FragmentAddSoundsBinding.inflate(layoutInflater)
        binding.viewModel = viewModel

        binding.duplicateAdd.setOnCheckedChangeListener { _, isChecked -> viewModel.setDuplicateAdd(isChecked) }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel) { _, _ -> dismiss() }
            .setView(binding.root)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { onPositiveButtonClick() }
        }

        return dialog
    }

    private fun onPositiveButtonClick() {
        val soundName = binding.soundName.text.toString().trim()
        if (soundName.isEmpty() && !multiple)
            Snackbar.make(binding.root, R.string.name_cannot_be_empty, Snackbar.LENGTH_SHORT).show()
        else {
            viewModel.save(soundName, binding.volume.progress, binding.category.selectedItem as Category)
            dismiss()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        /**
         * Observe viewModel.categories to set spinner items.
         * Observe viewModel.multiple to set dialog title.
         */
        super.onViewCreated(view, savedInstanceState)

        viewModel.duplicateData.observe(this) {
            binding.duplicateText.text = Html.fromHtml(
                resources.getQuantityString(
                    R.plurals.sound_already_exists,
                    it.count,
                    it.count,
                    TextUtils.htmlEncode(it.name)
                ),
                Html.FROM_HTML_MODE_LEGACY
            )
        }

        viewModel.categories.observe(this) {
            binding.category.adapter = CategorySpinnerAdapter(requireContext(), it)
        }

        viewModel.multiple.observe(this) {
            multiple = it
            dialog?.setTitle(resources.getQuantityString(R.plurals.add_sound, if (it) 2 else 1))
            dialog?.show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.setName(binding.soundName.text)
        viewModel.setCategory(binding.category.selectedItem as Category)
        viewModel.setVolume(binding.volume.progress)
        viewModel.setDuplicateAdd(binding.duplicateAdd.isChecked)
    }
}