package us.huseli.soundboard2.ui

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import us.huseli.soundboard2.R
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.databinding.FragmentAddSoundsBinding
import us.huseli.soundboard2.viewmodels.SoundAddViewModel

class SoundAddFragment : BaseDialogFragment<FragmentAddSoundsBinding>() {
    private val viewModel by activityViewModels<SoundAddViewModel>()
    private var multiple = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = FragmentAddSoundsBinding.inflate(layoutInflater)
        binding.viewModel = viewModel

        binding.duplicateAdd.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setDuplicateAdd(isChecked)
        }

        binding.volume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                viewModel.volume = progress
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.category.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.selectedCategoryPosition.value = position
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.soundName.addTextChangedListener {
            if (it != null) viewModel.setName(it)
        }

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        /**
         * Observe viewModel.categories to set spinner items.
         * Observe viewModel.multiple to set dialog title.
         */
        super.onViewCreated(view, savedInstanceState)

        viewModel.categories.observe(this) {
            binding.category.adapter = CategorySpinnerAdapter(requireContext(), it)
        }

        viewModel.multiple.observe(this) {
            multiple = it
            dialog?.setTitle(resources.getQuantityString(R.plurals.add_sound, if (it) 2 else 1))
            dialog?.show()
        }
    }
}