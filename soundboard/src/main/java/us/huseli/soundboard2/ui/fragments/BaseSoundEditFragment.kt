package us.huseli.soundboard2.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import androidx.annotation.ColorInt
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.google.android.material.snackbar.Snackbar
import us.huseli.soundboard2.R
import us.huseli.soundboard2.databinding.FragmentEditSoundsBinding
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.helpers.ValidationError
import us.huseli.soundboard2.ui.CategorySpinnerAdapter
import us.huseli.soundboard2.viewmodels.BaseSoundEditViewModel

abstract class BaseSoundEditFragment : LoggingObject, BaseColorPickerDialogFragment<FragmentEditSoundsBinding>() {
    @get:PluralsRes
    protected abstract val dialogTitlePlural: Int
    protected abstract val viewModel: BaseSoundEditViewModel

    @StringRes
    override val colorPickerDialogTitle = R.string.select_background_colour
    @StringRes
    override val positiveButtonText = R.string.save
    @StringRes
    override val negativeButtonText = R.string.cancel

    override fun getSelectColorButton(binding: FragmentEditSoundsBinding) = binding.column2.selectColorButton

    override fun onCreateBinding(layoutInflater: LayoutInflater, savedInstanceState: Bundle?) =
        FragmentEditSoundsBinding.inflate(layoutInflater)

    override fun onBindingCreated(binding: FragmentEditSoundsBinding) {
        super.onBindingCreated(binding)

        binding.viewModel = viewModel

        viewModel.categories.observe(this) {
            binding.column1.category.adapter = CategorySpinnerAdapter(requireContext(), it)
        }

        viewModel.isReady.observe(this) {
            binding.progressCircle.visible = !it
        }
    }

    override fun onDialogCreated(dialog: AlertDialog) {
        super.onDialogCreated(dialog)

        viewModel.soundCount.observe(this) {
            dialog.setTitle(resources.getQuantityString(dialogTitlePlural, it))
            dialog.show()
        }

        viewModel.isReady.observe(this) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = it
        }
    }

    override fun onColorSelected(dialogId: Int, @ColorInt color: Int) {
        viewModel.backgroundColor.value = color
    }

    override fun onPositiveButtonClick(): Boolean {
        return try {
            viewModel.save()
            true
        } catch (e: ValidationError) {
            Snackbar.make(binding.root, e.messageResId, Snackbar.LENGTH_SHORT).show()
            false
        }
    }
}