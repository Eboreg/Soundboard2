package us.huseli.soundboard2.ui

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import dagger.hilt.android.AndroidEntryPoint
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.R
import us.huseli.soundboard2.databinding.FragmentAddCategoryBinding
import us.huseli.soundboard2.helpers.ColorHelper
import us.huseli.soundboard2.viewmodels.CategoryAddViewModel
import javax.inject.Inject

@AndroidEntryPoint
class CategoryAddFragment : DialogFragment(), ColorPickerDialogListener {
    private lateinit var binding: FragmentAddCategoryBinding
    private val viewModel by activityViewModels<CategoryAddViewModel>()
    @Inject lateinit var colorHelper: ColorHelper

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        viewModel.reset()
        binding = FragmentAddCategoryBinding.inflate(layoutInflater)
        binding.viewModel = viewModel
        binding.selectColorButton.setOnClickListener { onSelectColorClick() }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_category)
            .setView(binding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel) { _, _ -> dismiss() }
            .create()

        // Custom listener to avoid automatic dismissal!
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { onPositiveButtonClick() }
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        binding.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.lifecycleOwner = viewLifecycleOwner
    }

    override fun onColorSelected(dialogId: Int, color: Int) {
        Log.d(LOG_TAG, "onColorSelected: dialogId=$dialogId, color=$color")
        viewModel.setBackgroundColor(color)
    }

    override fun onDialogDismissed(dialogId: Int) {}

    private fun onPositiveButtonClick() {
        val catName = binding.categoryName.text.toString().trim()
        if (catName.isEmpty())
            Snackbar.make(binding.root, R.string.name_cannot_be_empty, Snackbar.LENGTH_SHORT).show()
        else {
            viewModel.setName(catName)
            viewModel.save()
            dismiss()
        }
    }

    private fun onSelectColorClick() {
        ColorPickerDialog.newBuilder().apply {
            setPresets(colorHelper.colors.toIntArray())
            setDialogTitle(R.string.select_background_colour)
            viewModel.backgroundColor.value?.let { setColor(it) }
            setDialogId(Constants.FRAGMENT_TAGS.indexOf("CategoryAddFragment"))
            show(requireActivity())
        }
    }

    companion object {
        const val LOG_TAG = "CategoryAddFragment"
    }
}