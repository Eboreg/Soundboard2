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
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.repositories.SettingsRepository
import us.huseli.soundboard2.databinding.ActivitySettingsBinding
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.viewmodels.SettingsViewModel
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : LoggingObject, AppCompatActivity() {
    @Inject
    lateinit var settingsRepository: SettingsRepository
    private lateinit var binding: ActivitySettingsBinding
    private val viewModel by viewModels<SettingsViewModel>()
    private var watchFolderUri: Uri? = null
    private var watchFolderLauncher = registerForActivityResult(GetWatchFolder()) { onWatchFolderSelected(it) }

    private inner class GetWatchFolder : ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?): Intent {
            this@SettingsActivity.overridePendingTransition(0, 0)
            val intent = super.createIntent(context, input).addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                watchFolderUri?.also { intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
            }
            return intent
        }
    }

    private fun onWatchFolderSelected(uri: Uri?) {
        if (uri != null) {
            applicationContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            watchFolderUri = uri
            viewModel.setWatchFolderUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        setContentView(binding.root)

        viewModel.watchFolderUri.observe(this) { watchFolderUri = it }
        viewModel.watchFolderString.observe(this) { binding.watchFolderString.setText(it) }
        viewModel.watchFolderCategoryPosition.observe(this) { binding.watchFolderCategory.setSelection(it) }

        viewModel.categories.observe(this) {
            binding.watchFolderCategory.adapter = CategorySpinnerAdapter(this, it)
        }

        binding.watchFolderSelectButton.setOnClickListener { watchFolderLauncher.launch(watchFolderUri) }

        binding.isWatchFolderEnabled.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setWatchFolderEnabled(isChecked)
        }

        binding.isAnimationEnabled.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAnimationEnabled(isChecked)
        }

        binding.saveButton.setOnClickListener {
            viewModel.save(
                binding.isAnimationEnabled.isChecked,
                binding.isWatchFolderEnabled.isChecked,
                watchFolderUri,
                binding.watchFolderCategory.selectedItem as? Category,
                binding.watchFolderTrashMissing.isChecked
            )
            finish()
        }

        binding.cancelButton.setOnClickListener { finish() }

        viewModel.isWatchFolderEnabled.observe(this) { isChecked ->
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
}