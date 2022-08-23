package us.huseli.soundboard2.ui

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
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
    @Inject lateinit var settingsRepository: SettingsRepository
    private lateinit var binding: ActivitySettingsBinding
    private val viewModel by viewModels<SettingsViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        setContentView(binding.root)
        lifecycle.addObserver(settingsRepository)

        viewModel.categories.observe(this) {
            binding.watchFolderCategory.adapter = CategorySpinnerAdapter(this, it)
        }

        binding.watchFolderCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.setWatchFolderCategoryPosition(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                viewModel.setWatchFolderCategoryPosition(0)
            }
        }

        binding.animationsEnabled.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAnimationsEnabled(isChecked)
        }

        binding.saveButton.setOnClickListener {
            viewModel.save()
            finish()
        }

        binding.cancelButton.setOnClickListener { finish() }
    }

    override fun onPause() {
        super.onPause()
        overridePendingTransition(0, 0)
    }
}