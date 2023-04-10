package us.huseli.soundboard2.ui.fragments

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import androidx.fragment.app.activityViewModels
import us.huseli.soundboard2.R
import us.huseli.soundboard2.databinding.FragmentBackupBinding
import us.huseli.soundboard2.viewmodels.BackupViewModel
import java.io.FileOutputStream

class BackupFragment : BaseBackupRestoreFragment() {
    override val viewModel by activityViewModels<BackupViewModel>()
    @StringRes
    override val dialogTitle = R.string.backup

    private inner class BackupFileContract : ActivityResultContracts.CreateDocument("application/zip") {
        override fun createIntent(context: Context, input: String): Intent {
            val intent = super.createIntent(context, input)
            viewModel.backupFile?.let { intent.putExtra(Intent.EXTRA_TITLE, it.name) }
            return intent
        }
    }

    private val backupFileLauncher = registerForActivityResult(BackupFileContract()) {
        it?.let { uri ->
            try {
                requireContext().contentResolver.openFileDescriptor(uri, "w")?.use { descriptor ->
                    FileOutputStream(descriptor.fileDescriptor).use { outputStream ->
                        outputStream.write(viewModel.backupFile?.readBytes())
                    }
                }
            } catch (e: Exception) {
                viewModel.setError(e.toString())
            }
        }
    }

    override fun onBindingCreated(binding: FragmentBackupBinding) {
        super.onBindingCreated(binding)

        viewModel.backup(includeSounds = arguments?.getBoolean("includeSounds", true) ?: true)

        binding.backupSaveButton.setOnClickListener {
            backupFileLauncher.launch("application/zip")
        }

        binding.backupShareButton.setOnClickListener {
            viewModel.backupFile?.let { backupFile ->
                val uri = FileProvider.getUriForFile(requireContext(), "us.huseli.soundboard2.fileprovider", backupFile)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    setDataAndType(uri, requireContext().contentResolver.getType(uri))
                }
                startActivity(Intent.createChooser(intent, getString(R.string.share_with)))
            }
        }
    }
}