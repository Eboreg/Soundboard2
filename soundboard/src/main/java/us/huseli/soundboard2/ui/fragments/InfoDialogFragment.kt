package us.huseli.soundboard2.ui.fragments

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import us.huseli.soundboard2.R

class InfoDialogFragment(private val message: Int) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setPositiveButton(R.string.ok) { _, _ -> dismiss() }
            .setMessage(message)
            .create()
    }
}