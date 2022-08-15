package us.huseli.soundboard2.ui

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
import us.huseli.soundboard2.helpers.SoundSorting
import us.huseli.soundboard2.viewmodels.CategoryEditViewModel

@AndroidEntryPoint
class CategoryEditFragment : BaseCategoryEditFragment<FragmentEditCategoryBinding>() {
    override val viewModel by activityViewModels<CategoryEditViewModel>()
    private val sortParameterItems = listOf(
        SortParameterItem(SoundSorting.Parameter.UNDEFINED, R.string.unchanged),
        SortParameterItem(SoundSorting.Parameter.NAME, R.string.name),
        SortParameterItem(SoundSorting.Parameter.DURATION, R.string.duration),
        SortParameterItem(SoundSorting.Parameter.TIME_ADDED, R.string.creation_time),
    )

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        viewModel.reset()
        binding = FragmentEditCategoryBinding.inflate(layoutInflater)
        binding.viewModel = viewModel
        binding.selectColorButton.setOnClickListener { onSelectColorClick() }

        binding.categoryName.addTextChangedListener {
            if (it != null) viewModel.setName(it)
        }

        binding.sortBy.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            sortParameterItems
        )

        binding.sortBy.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                (parent?.getItemAtPosition(position) as? SortParameterItem)?.let {
                    viewModel.sortParameter.value = it.value
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                viewModel.sortParameter.value = SoundSorting.Parameter.UNDEFINED
            }
        }

        binding.sortOrder.setOnCheckedChangeListener { _, checkedId ->
            viewModel.sortOrder.value = when(checkedId) {
                binding.sortOrderDescending.id -> SoundSorting.Order.DESCENDING
                else -> SoundSorting.Order.ASCENDING
            }
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

    private fun onPositiveButtonClick() {
        val catName = binding.categoryName.text
        if (catName.trim().isEmpty())
            Snackbar.make(binding.root, R.string.name_cannot_be_empty, Snackbar.LENGTH_SHORT).show()
        else {
            viewModel.save(
                catName,
                SoundSorting(
                    (binding.sortBy.selectedItem as SortParameterItem).value,
                    when (binding.sortOrder.checkedRadioButtonId) {
                        binding.sortOrderDescending.id -> SoundSorting.Order.DESCENDING
                        else -> SoundSorting.Order.ASCENDING
                    }
                )
            )
            dismiss()
        }
    }

    inner class SortParameterItem(val value: SoundSorting.Parameter, private val stringRes: Int) {
        override fun toString() = getString(stringRes)
    }
}