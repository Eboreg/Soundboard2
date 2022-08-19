package us.huseli.soundboard2.ui.fragments

import androidx.databinding.ViewDataBinding
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.R
import us.huseli.soundboard2.helpers.ColorHelper
import us.huseli.soundboard2.viewmodels.BaseCategoryEditViewModel
import javax.inject.Inject

abstract class BaseCategoryEditFragment<T: ViewDataBinding> : BaseDialogFragment<T>(), ColorPickerDialogListener {
    protected abstract val viewModel: BaseCategoryEditViewModel
    @Inject lateinit var colorHelper: ColorHelper

    override fun onColorSelected(dialogId: Int, color: Int) {
        viewModel.setBackgroundColor(color)
    }

    override fun onDialogDismissed(dialogId: Int) {}

    protected fun onSelectColorClick() {
        val className = javaClass.simpleName

        ColorPickerDialog.newBuilder().apply {
            setPresets(colorHelper.colors.toIntArray())
            setDialogTitle(R.string.select_background_colour)
            viewModel.backgroundColor.value?.let { setColor(it) }
            setDialogId(Constants.FRAGMENT_TAGS.indexOf(className))
            show(requireActivity())
        }
    }
}