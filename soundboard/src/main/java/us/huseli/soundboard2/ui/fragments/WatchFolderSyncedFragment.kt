package us.huseli.soundboard2.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.StringRes
import androidx.fragment.app.activityViewModels
import us.huseli.soundboard2.R
import us.huseli.soundboard2.databinding.FragmentWatchFolderSyncedBinding
import us.huseli.soundboard2.viewmodels.AppViewModel

class WatchFolderSyncedFragment : BaseDialogFragment<FragmentWatchFolderSyncedBinding>() {
    private val appViewModel by activityViewModels<AppViewModel>()
    private var added: Array<String> = emptyArray()
    private var deleted: Array<String> = emptyArray()

    @StringRes
    override val positiveButtonText = R.string.ok
    @StringRes
    override val dialogTitle = R.string.watched_folder_sync

    override fun onBindingCreated(binding: FragmentWatchFolderSyncedBinding) {
        super.onBindingCreated(binding)

        if (added.isNotEmpty()) {
            appViewModel.watchFolderCategory.observe(this) {
                it?.let { category ->
                    binding.addedHeader.text = resources.getQuantityString(
                        R.plurals.sounds_automatically_added,
                        added.size,
                        category.name
                    )
                }
            }
            binding.added.text = added.joinToString("\n")
            binding.addedSection.visibility = View.VISIBLE
        } else {
            binding.addedSection.visibility = View.GONE
        }

        if (deleted.isNotEmpty()) {
            binding.deletedHeader.text = resources.getQuantityString(
                R.plurals.sounds_automatically_deleted,
                deleted.size
            )
            binding.deleted.text = deleted.joinToString("\n")
            binding.deletedSection.visibility = View.VISIBLE
        } else {
            binding.deletedSection.visibility = View.GONE
        }
    }

    override fun onCreateBinding(
        layoutInflater: LayoutInflater,
        savedInstanceState: Bundle?
    ): FragmentWatchFolderSyncedBinding {
        requireArguments().getStringArray("deleted")?.let { deleted = it }
        requireArguments().getStringArray("added")?.let { added = it }
        return FragmentWatchFolderSyncedBinding.inflate(layoutInflater)
    }
}