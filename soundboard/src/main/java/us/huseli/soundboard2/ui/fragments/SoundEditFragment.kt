package us.huseli.soundboard2.ui.fragments

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import us.huseli.soundboard2.R
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.databinding.FragmentEditSoundsBinding
import us.huseli.soundboard2.ui.CategorySpinnerAdapter
import us.huseli.soundboard2.viewmodels.SoundEditViewModel

class SoundEditFragment : BaseDialogFragment<FragmentEditSoundsBinding>() {
    private val viewModel by activityViewModels<SoundEditViewModel>()
    private var multiple = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = FragmentEditSoundsBinding.inflate(layoutInflater)
        binding.viewModel = viewModel

        binding.keepVolume.setOnCheckedChangeListener { _, isChecked ->
            binding.volume.isEnabled = !isChecked
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel) { _, _ -> dismiss() }
            .setTitle(resources.getQuantityString(R.plurals.edit_sounds, 1))
            .setView(binding.root)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { onPositiveButtonClick() }
        }

        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.soundCount.observe(this) {
            multiple = it > 1
            dialog?.setTitle(resources.getQuantityString(R.plurals.edit_sounds, it))
            dialog?.show()
        }

        viewModel.categories.observe(this) {
            binding.category.adapter = CategorySpinnerAdapter(requireContext(), it)
        }
    }

    private fun onPositiveButtonClick() {
        val soundName = binding.soundName.text.toString().trim()
        if (soundName.isEmpty() && !multiple) {
            Snackbar.make(binding.root, R.string.name_cannot_be_empty, Snackbar.LENGTH_SHORT).show()
        }
        else {
            viewModel.save(
                if (!multiple) soundName else null,
                binding.keepVolume.isChecked,
                binding.volume.progress,
                binding.category.selectedItem as Category
            )
            dismiss()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.setCategory(binding.category.selectedItem as Category)
        viewModel.setName(binding.soundName.text)
        viewModel.setVolume(binding.volume.progress)
        viewModel.setKeepVolume(binding.keepVolume.isChecked)
    }
}