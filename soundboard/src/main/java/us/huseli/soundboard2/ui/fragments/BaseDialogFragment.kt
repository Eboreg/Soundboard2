package us.huseli.soundboard2.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

abstract class BaseDialogFragment<T : ViewDataBinding> : DialogFragment() {
    /**
     * Order:
     *
     * onCreate()
     * onCreateDialog()     Returns Dialog, sets this.dialog
     * onCreateBinding()    Returns ViewDataBinding, sets this.binding
     * onBindingCreated()   Convenience method to do stuff on this.binding
     * onDialogCreated()    Convenience method to do stuff on this.dialog
     * onCreateView()       Returns root View, viewLifecycleOwner can be used here
     * onViewCreated()      Convenience method to do stuff on root View
     */
    protected open lateinit var binding: T

    val dialogId = javaClass.hashCode()

    @StringRes
    open val dialogTitle: Int? = null
    @StringRes
    open val positiveButtonText: Int? = null
    @StringRes
    open val negativeButtonText: Int? = null

    abstract fun onCreateBinding(layoutInflater: LayoutInflater, savedInstanceState: Bundle?): T

    open fun onBindingCreated(binding: T) {}
    open fun onDialogCreated(dialog: AlertDialog) {}
    @Suppress("SameReturnValue")
    open fun onPositiveButtonClick(): Boolean = true
    @Suppress("SameReturnValue")
    open fun onNegativeButtonClick(): Boolean = true

    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        binding = onCreateBinding(layoutInflater, savedInstanceState)
        onBindingCreated(binding)

        val builder = MaterialAlertDialogBuilder(requireContext()).setView(binding.root)
        dialogTitle?.let { builder.setTitle(it) }
        positiveButtonText?.let { builder.setPositiveButton(it, null) }
        negativeButtonText?.let { builder.setNegativeButton(it, null) }

        val dialog = builder.create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                if (onPositiveButtonClick()) dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
                if (onNegativeButtonClick()) dismiss()
            }
        }

        onDialogCreated(dialog)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }
}