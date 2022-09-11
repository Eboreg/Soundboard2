package us.huseli.soundboard2.ui.fragments

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import us.huseli.soundboard2.R
import us.huseli.soundboard2.databinding.FragmentEditCategoryBinding
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.helpers.SoundSorting
import us.huseli.soundboard2.viewmodels.CategoryEditViewModel

@AndroidEntryPoint
class CategoryEditFragment : LoggingObject, BaseCategoryEditFragment<FragmentEditCategoryBinding>() {
    override val viewModel by activityViewModels<CategoryEditViewModel>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = FragmentEditCategoryBinding.inflate(layoutInflater)
        binding.viewModel = viewModel
        binding.selectColorButton.setOnClickListener { onSelectColorClick() }
        binding.categoryName.addTextChangedListener { if (it != null) viewModel.setName(it) }

        binding.sortBy.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            SoundSorting.listSortParameterItems(requireContext())
        )

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_category)
            .setView(binding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel) { _, _ -> dismiss() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { onPositiveButtonClick() }
        }

        return dialog
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.setSortParameter((binding.sortBy.selectedItem as SoundSorting.SortParameterItem).value)
        viewModel.setSortOrder(if (binding.sortOrderAscending.isChecked) SoundSorting.Order.ASCENDING else SoundSorting.Order.DESCENDING)
    }

    private fun onPositiveButtonClick() {
        val catName = binding.categoryName.text
        if (catName.trim().isEmpty())
            Snackbar.make(binding.root, R.string.name_cannot_be_empty, Snackbar.LENGTH_SHORT).show()
        else {
            viewModel.save(catName)
            dismiss()
        }
    }
}