package us.huseli.soundboard2.ui

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import us.huseli.soundboard2.R
import us.huseli.soundboard2.databinding.FragmentAddCategoryBinding
import us.huseli.soundboard2.viewmodels.CategoryAddViewModel

@AndroidEntryPoint
class CategoryAddFragment : BaseCategoryEditFragment<FragmentAddCategoryBinding>() {
    override val viewModel by activityViewModels<CategoryAddViewModel>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        viewModel.reset()
        binding = FragmentAddCategoryBinding.inflate(layoutInflater)
        binding.viewModel = viewModel
        binding.selectColorButton.setOnClickListener { onSelectColorClick() }
        binding.categoryName.addTextChangedListener { if (it != null) viewModel.setName(it) }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_category)
            .setView(binding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel) { _, _ -> dismiss() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { onPositiveButtonClick() }
        }

        return dialog
    }

    private fun onPositiveButtonClick() {
        val catName = binding.categoryName.text
        if (catName.trim().isEmpty())
            Snackbar.make(binding.root, R.string.name_cannot_be_empty, Snackbar.LENGTH_SHORT).show()
        else {
            viewModel.setName(catName)
            viewModel.save()
            dismiss()
        }
    }
}