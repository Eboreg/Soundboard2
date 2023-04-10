package us.huseli.soundboard2.ui

import android.animation.LayoutTransition
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.os.bundleOf
import dagger.hilt.android.AndroidEntryPoint
import us.huseli.soundboard2.data.repositories.SettingsRepository
import us.huseli.soundboard2.databinding.ActivitySettingsBinding
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.ui.fragments.BackupFragment
import us.huseli.soundboard2.ui.fragments.RestoreFragment
import us.huseli.soundboard2.viewmodels.SettingsViewModel
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : LoggingObject, BaseActivity<ActivitySettingsBinding>() {
    @Inject
    lateinit var settingsRepository: SettingsRepository
    private val settingsViewModel by viewModels<SettingsViewModel>()
    private var watchFolderUri: Uri? = null
    private val watchFolderLauncher = registerForActivityResult(WatchFolderContract()) { onWatchFolderSelected(it) }
    // private val restoreFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
    private val restoreFileLauncher = registerForActivityResult(RestoreFileContract()) { uri ->
        showFragment(
            RestoreFragment::class.java,
            bundleOf(Pair("uri", uri))
        )
    }

    private class RestoreFileContract : ActivityResultContracts.OpenDocument() {
        override fun createIntent(context: Context, input: Array<String>): Intent {
            return super.createIntent(context, input).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private inner class WatchFolderContract : ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?): Intent {
            this@SettingsActivity.overridePendingTransition(0, 0)
            val intent = super.createIntent(context, input).addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                watchFolderUri?.also { intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
            }
            return intent
        }
    }

    /** OVERRIDDEN METHODS ***************************************************/

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        binding.lifecycleOwner = this
        binding.viewModel = settingsViewModel
        setContentView(binding.root)

        settingsViewModel.watchFolderUri.observe(this) { watchFolderUri = it }

        settingsViewModel.categories.observe(this) {
            binding.watchFolderCategory.adapter = CategorySpinnerAdapter(this, it)
        }

        binding.saveButton.setOnClickListener {
            settingsViewModel.save()
            finish()
        }

        binding.watchFolderSelectButton.setOnClickListener { watchFolderLauncher.launch(watchFolderUri) }
        binding.cancelButton.setOnClickListener { finish() }
        binding.backupButton.setOnClickListener { startBackup() }
        binding.restoreButton.setOnClickListener { startRestore() }

        settingsViewModel.isWatchFolderEnabledLive.observe(this) { isChecked ->
            if (isChecked) {
                // Expand; 0 to wrap_content
                binding.watchFolderOptions.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                binding.watchFolderOptions.requestLayout()
            } else {
                // Collapse; wrap_content to 0
                binding.watchFolderOptions.layoutParams.height = 0
                binding.watchFolderOptions.requestLayout()
            }
            binding.watchFolderOptions.layoutTransition?.enableTransitionType(LayoutTransition.CHANGING)
        }
    }

    override fun onPause() {
        super.onPause()
        overridePendingTransition(0, 0)
    }

    override fun onStart() {
        super.onStart()
        settingsRepository.initialize()
    }

    private fun startBackup() {
        showFragment(BackupFragment::class.java, bundleOf(Pair("includeSounds", binding.backupIncludeSounds.isChecked)))
    }

    private fun startRestore() {
        /*
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            type = "application/zip"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        restoreFileLauncher.launch(intent)
         */
        restoreFileLauncher.launch(arrayOf("application/zip"))
    }

    /** PRIVATE METHODS ******************************************************/

    private fun onWatchFolderSelected(uri: Uri?) {
        if (uri != null) {
            applicationContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            watchFolderUri = uri
            settingsViewModel.setWatchFolderUri(uri)
        }
    }
}