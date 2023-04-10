package us.huseli.soundboard2.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.google.android.material.snackbar.Snackbar
import us.huseli.soundboard2.R
import us.huseli.soundboard2.databinding.FragmentEditCategoryBinding
import us.huseli.soundboard2.helpers.SoundSorting
import us.huseli.soundboard2.helpers.ValidationError
import us.huseli.soundboard2.viewmodels.BaseCategoryEditViewModel

abstract class BaseCategoryEditFragment : BaseColorPickerDialogFragment<FragmentEditCategoryBinding>() {
    protected abstract val viewModel: BaseCategoryEditViewModel
    @StringRes
    override val colorPickerDialogTitle = R.string.select_background_colour
    @StringRes
    override val positiveButtonText = R.string.save
    @StringRes
    override val negativeButtonText = R.string.cancel

    @ColorInt
    override fun getInitialColor() = viewModel.backgroundColor.value

    override fun getSelectColorButton(binding: FragmentEditCategoryBinding) = binding.column1.selectColorButton

    override fun onBindingCreated(binding: FragmentEditCategoryBinding) {
        super.onBindingCreated(binding)

        binding.viewModel = viewModel

        binding.column2.sortBy.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            SoundSorting.listSortParameterItems(requireContext())
        )

        binding.column1.randomColorButton.setOnClickListener { viewModel.setRandomBackgroundColor() }

        viewModel.isReady.observe(this) {
            binding.progressCircle.visible = !it
        }
    }

    override fun onCreateBinding(layoutInflater: LayoutInflater, savedInstanceState: Bundle?) =
        FragmentEditCategoryBinding.inflate(layoutInflater)

    override fun onColorSelected(dialogId: Int, color: Int) {
        viewModel.backgroundColor.value = color
    }

    override fun onDialogCreated(dialog: AlertDialog) {
        super.onDialogCreated(dialog)

        viewModel.isReady.observe(this) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = it
        }
    }

    override fun onPositiveButtonClick(): Boolean {
        return try {
            viewModel.validate()
            viewModel.save()
            true
        } catch (e: ValidationError) {
            Snackbar.make(binding.root, e.messageResId, Snackbar.LENGTH_SHORT).show()
            false
        }
    }
}