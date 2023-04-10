package us.huseli.soundboard2.ui.fragments

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import us.huseli.soundboard2.R
import us.huseli.soundboard2.databinding.FragmentDeleteCategoryBinding
import us.huseli.soundboard2.ui.CategorySpinnerAdapter
import us.huseli.soundboard2.viewmodels.CategoryDeleteViewModel

class CategoryDeleteFragment : BaseDialogFragment<FragmentDeleteCategoryBinding>() {
    private val viewModel by activityViewModels<CategoryDeleteViewModel>()
    @StringRes
    override val positiveButtonText = R.string.delete
    @StringRes
    override val negativeButtonText = R.string.cancel
    @StringRes
    override val dialogTitle = R.string.delete_category

    override fun onBindingCreated(binding: FragmentDeleteCategoryBinding) {
        super.onBindingCreated(binding)
        binding.viewModel = viewModel

        viewModel.otherCategories.observe(this) {
            binding.newCategory.adapter = CategorySpinnerAdapter(requireContext(), it)
        }
    }

    override fun onCreateBinding(layoutInflater: LayoutInflater, savedInstanceState: Bundle?) =
        FragmentDeleteCategoryBinding.inflate(layoutInflater)

    override fun onDialogCreated(dialog: AlertDialog) {
        super.onDialogCreated(dialog)

        viewModel.isSaveEnabled.observe(this) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = it
        }

        viewModel.name.observe(this) { name ->
            dialog.setTitle(
                if (name != null) getString(R.string.delete_category_name, name)
                else getString(R.string.delete_category)
            )
            dialog.show()
        }

        viewModel.isLastCategory.observe(this) { isLastCategory ->
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).isVisible = !isLastCategory
            dialog.show()
        }
    }

    override fun onPositiveButtonClick(): Boolean {
        viewModel.delete()
        return true
    }
}