package us.huseli.soundboard2.ui.fragments

import android.widget.Button
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.databinding.ViewDataBinding
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import us.huseli.soundboard2.helpers.ColorHelper
import javax.inject.Inject

abstract class BaseColorPickerDialogFragment<T : ViewDataBinding> : BaseDialogFragment<T>(), ColorPickerDialogListener {
    @Inject
    lateinit var colorHelper: ColorHelper
    @get:StringRes
    abstract val colorPickerDialogTitle: Int

    abstract fun getSelectColorButton(binding: T): Button
    @ColorInt
    open fun getInitialColor(): Int? = null

    override fun onBindingCreated(binding: T) {
        super.onBindingCreated(binding)
        getSelectColorButton(binding).setOnClickListener { onSelectColorClick() }
    }

    override fun onDialogDismissed(dialogId: Int) {}

    private fun onSelectColorClick() {
        val builder = ColorPickerDialog.newBuilder()
            .setPresets(colorHelper.colors.toIntArray())
            .setDialogTitle(colorPickerDialogTitle)
            .setDialogId(dialogId)
        getInitialColor()?.let { builder.setColor(it) }
        builder.show(requireActivity())
    }
}