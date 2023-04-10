package us.huseli.soundboard2.ui.fragments

import android.net.Uri
import android.os.Build
import androidx.annotation.StringRes
import androidx.fragment.app.activityViewModels
import us.huseli.soundboard2.R
import us.huseli.soundboard2.databinding.FragmentBackupBinding
import us.huseli.soundboard2.viewmodels.RestoreViewModel

class RestoreFragment : BaseBackupRestoreFragment() {
    override val viewModel by activityViewModels<RestoreViewModel>()
    @StringRes
    override val dialogTitle = R.string.restore

    override fun onBindingCreated(binding: FragmentBackupBinding) {
        super.onBindingCreated(binding)

        // val resultCode = arguments?.getInt("resultCode", 0)
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable("uri", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable("uri") as? Uri
        }

        // if (resultCode == Activity.RESULT_OK && uri != null) viewModel.restore(uri)
        if (uri != null) viewModel.restore(uri)
        else viewModel.setError(getString(R.string.no_file_chosen))
    }
}