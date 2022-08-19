package us.huseli.soundboard2.ui.fragments

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import us.huseli.soundboard2.R
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.databinding.FragmentDeleteCategoryBinding
import us.huseli.soundboard2.ui.CategorySpinnerAdapter
import us.huseli.soundboard2.viewmodels.CategoryDeleteViewModel

@AndroidEntryPoint
class CategoryDeleteFragment : BaseDialogFragment<FragmentDeleteCategoryBinding>() {
    private val viewModel by activityViewModels<CategoryDeleteViewModel>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = FragmentDeleteCategoryBinding.inflate(layoutInflater).also { it.viewModel = viewModel }

        return MaterialAlertDialogBuilder(requireContext())
            .setPositiveButton(R.string.delete) { _, _ -> delete() }
            .setNegativeButton(R.string.cancel) { _, _ -> dismiss() }
            .setTitle(R.string.delete_category)
            .setView(binding.root)
            .create()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.otherCategories.observe(this) {
            binding.newCategory.adapter = CategorySpinnerAdapter(requireContext(), it)
        }

        viewModel.name.observe(this) { name ->
            dialog?.setTitle(
                if (name != null) getString(R.string.delete_category_name, name)
                else getString(R.string.delete_category)
            )
            dialog?.show()
        }

        viewModel.isLastCategory.observe(this) { isLastCategory ->
            (dialog as AlertDialog?)?.let {
                it.getButton(DialogInterface.BUTTON_POSITIVE).isVisible = !isLastCategory
                it.show()
            }
        }

        binding.soundAction.setOnCheckedChangeListener { _, checkedId ->
            binding.newCategoryBlock.isVisible = checkedId == R.id.soundActionMove
        }
    }

    private fun delete() {
        val moveTo =
            if (binding.soundActionMove.isChecked) binding.newCategory.selectedItem as Category
            else null
        viewModel.delete(moveTo?.id)
    }
}