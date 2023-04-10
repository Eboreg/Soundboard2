package us.huseli.soundboard2.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import us.huseli.soundboard2.R
import us.huseli.soundboard2.databinding.FragmentBackupBinding
import us.huseli.soundboard2.viewmodels.BaseBackupRestoreViewModel

abstract class BaseBackupRestoreFragment : BaseDialogFragment<FragmentBackupBinding>() {
    internal abstract val viewModel: BaseBackupRestoreViewModel

    @StringRes
    override val negativeButtonText = R.string.cancel
    @StringRes
    override val positiveButtonText = R.string.close

    override fun onBindingCreated(binding: FragmentBackupBinding) {
        super.onBindingCreated(binding)
        binding.viewModel = viewModel
    }

    override fun onCreateBinding(layoutInflater: LayoutInflater, savedInstanceState: Bundle?) =
        FragmentBackupBinding.inflate(layoutInflater)

    override fun onDialogCreated(dialog: AlertDialog) {
        super.onDialogCreated(dialog)
        viewModel.isInProgress.observe(this) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.visibility = if (it) View.VISIBLE else View.GONE
            dialog.show()
        }
        viewModel.isFinished.observe(this) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.visibility = if (it) View.VISIBLE else View.GONE
            dialog.show()
        }
        viewModel.hasError.observe(this) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.visibility = if (it) View.VISIBLE else View.GONE
            dialog.show()
        }
    }

    override fun onNegativeButtonClick(): Boolean {
        viewModel.cancelCurrentJob()
        return true
    }
}