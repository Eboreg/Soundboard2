package us.huseli.soundboard2.ui.fragments

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import us.huseli.soundboard2.R

class InfoDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext())
            .setPositiveButton(R.string.ok) { _, _ -> dismiss() }
            .setMessage(requireArguments().getCharSequence("message"))
            .create()
}