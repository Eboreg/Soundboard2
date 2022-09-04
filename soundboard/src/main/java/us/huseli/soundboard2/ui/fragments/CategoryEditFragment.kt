package us.huseli.soundboard2.ui.fragments

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
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
        viewModel.reset()
        binding = FragmentEditCategoryBinding.inflate(layoutInflater)
        binding.viewModel = viewModel
        binding.selectColorButton.setOnClickListener { onSelectColorClick() }
        binding.categoryName.addTextChangedListener { if (it != null) viewModel.setName(it) }

        binding.sortBy.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            SoundSorting.getSortParameterItems(requireContext())
        )

        binding.sortBy.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                (parent?.getItemAtPosition(position) as? SoundSorting.SortParameterItem)?.let {
                    log("sortBy.onItemSelectedListener.onItemSelected: item=$it")
                    viewModel.setSortParameter(it.value)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                log("sortBy.onItemSelectedListener.onNothingSelected")
                viewModel.setSortParameter(SoundSorting.Parameter.CUSTOM)
            }
        }

        binding.sortOrder.setOnCheckedChangeListener { _, checkedId ->
            viewModel.setSortOrder(
                when (checkedId) {
                    binding.sortOrderDescending.id -> SoundSorting.Order.DESCENDING
                    else -> SoundSorting.Order.ASCENDING
                }
            )
        }

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.soundSorting.observe(this) {
            // Check appropriate 'order' radio button
            log("viewModel.soundSorting.observe: it=$it")
            binding.sortOrder.check(
                when (it.order) {
                    SoundSorting.Order.DESCENDING -> binding.sortOrderDescending.id
                    else -> binding.sortOrderAscending.id
                }
            )
            // Set sortBy spinner to appropriate value
            binding.sortBy.setSelection(
                SoundSorting.getSortParameterItems(requireContext())
                    .indexOfFirst { item -> item.value == it.parameter }
            )
        }
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