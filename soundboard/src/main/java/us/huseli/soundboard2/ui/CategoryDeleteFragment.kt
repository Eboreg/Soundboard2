package us.huseli.soundboard2.ui

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import us.huseli.soundboard2.BuildConfig
import us.huseli.soundboard2.R
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.databinding.FragmentDeleteCategoryBinding
import us.huseli.soundboard2.viewmodels.CategoryDeleteViewModel
import javax.inject.Inject

@AndroidEntryPoint
class CategoryDeleteFragment : DialogFragment() {
    @Inject lateinit var repository: CategoryRepository
    private lateinit var binding: FragmentDeleteCategoryBinding
    private val viewModel by activityViewModels<CategoryDeleteViewModel>()
    private var categoryId: Int? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        requireArguments().getInt("categoryId").let {
            categoryId = it
            viewModel.setCategoryId(it)
        }

        binding = FragmentDeleteCategoryBinding.inflate(layoutInflater)
        binding.viewModel = viewModel

        if (BuildConfig.DEBUG) Log.d(LOG_TAG, "onCreateDialog(): categoryId=$categoryId")

        return MaterialAlertDialogBuilder(requireContext())
            .setPositiveButton(R.string.delete) { _, _ -> delete() }
            .setNegativeButton(R.string.cancel) { _, _ -> dismiss() }
            .setTitle(R.string.delete_category)
            .setView(binding.root)
            .create()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        binding.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.lifecycleOwner = viewLifecycleOwner

        viewModel.categories.observe(this) {
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
        if (BuildConfig.DEBUG) Log.d(LOG_TAG, "delete(): categoryId=$categoryId, moveTo=$moveTo")
        categoryId?.let { viewModel.delete(it, moveTo?.id) }
    }

    companion object {
        const val LOG_TAG = "CategoryDeleteFragment"
    }
}