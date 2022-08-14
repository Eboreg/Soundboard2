package us.huseli.soundboard2.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import us.huseli.soundboard2.R
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.databinding.FragmentAddSoundsBinding
import us.huseli.soundboard2.viewmodels.SoundAddViewModel

class SoundAddFragment : DialogFragment() {
    private val viewModel by activityViewModels<SoundAddViewModel>()
    private lateinit var binding: FragmentAddSoundsBinding
    private var multiple = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = FragmentAddSoundsBinding.inflate(layoutInflater)
        binding.viewModel = viewModel

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel) { _, _ -> dismiss() }
            .setView(binding.root)
            .create()
        // Custom listener to avoid automatic dismissal!
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { onPositiveButtonClick() }
        }
        return dialog
    }

    private fun onPositiveButtonClick() {
        val soundName = binding.soundName.text.toString().trim()
        if (soundName.isEmpty() && !multiple) {
            Snackbar.make(binding.root, R.string.name_cannot_be_empty, Snackbar.LENGTH_SHORT).show()
        }
        else {
            viewModel.setName(soundName)
            viewModel.volume = binding.volume.progress
            viewModel.save(soundName, binding.volume.progress, binding.category.selectedItem as Category)
            dismiss()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        binding.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.lifecycleOwner = viewLifecycleOwner

        viewModel.categories.observe(this) {
            binding.category.adapter = CategorySpinnerAdapter(requireContext(), it)
        }

        viewModel.multiple.observe(this) {
            multiple = it
            dialog?.setTitle(resources.getQuantityString(R.plurals.add_sound, if (it) 2 else 1))
            dialog?.show()
        }

        viewModel.duplicateAdd.observe(this) {
            binding.duplicateAdd.isChecked = it
        }

        binding.duplicateAdd.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setDuplicateAdd(isChecked)
        }

        binding.volume
    }
}