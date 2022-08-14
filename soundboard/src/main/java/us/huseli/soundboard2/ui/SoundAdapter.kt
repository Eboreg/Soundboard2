package us.huseli.soundboard2.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import us.huseli.soundboard2.data.repositories.SoundRepository
import us.huseli.soundboard2.databinding.ItemSoundBinding
import us.huseli.soundboard2.helpers.ColorHelper
import us.huseli.soundboard2.viewmodels.SoundViewModel

class SoundAdapter(
    private val lifecycleOwner: LifecycleOwner,
    private val viewModelStore: ViewModelStore,
    private val soundRepository: SoundRepository,
    private val colorHelper: ColorHelper
) : ListAdapter<Int, SoundAdapter.ViewHolder>(Comparator()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemSoundBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), lifecycleOwner, viewModelStore, soundRepository, colorHelper)
    }

    class ViewHolder(private val binding: ItemSoundBinding) : RecyclerView.ViewHolder(binding.root) {
        internal fun bind(
            soundId: Int,
            lifecycleOwner: LifecycleOwner,
            viewModelStore: ViewModelStore,
            repository: SoundRepository,
            colorHelper: ColorHelper
        ) {
            val viewModel = ViewModelProvider(
                viewModelStore,
                SoundViewModel.Factory(repository, colorHelper, soundId)
            )[soundId.toString(), SoundViewModel::class.java]

            binding.lifecycleOwner = lifecycleOwner
            binding.viewModel = viewModel
        }
    }

    class Comparator: DiffUtil.ItemCallback<Int>() {
        override fun areItemsTheSame(oldItem: Int, newItem: Int) = oldItem == newItem
        override fun areContentsTheSame(oldItem: Int, newItem: Int) = oldItem == newItem
    }
}