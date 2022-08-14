package us.huseli.soundboard2.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.data.repositories.SoundRepository
import us.huseli.soundboard2.databinding.ItemCategoryBinding
import us.huseli.soundboard2.helpers.ColorHelper
import us.huseli.soundboard2.viewmodels.CategoryViewModel

class CategoryAdapter(
    private val activity: MainActivity,
    private val categoryRepository: CategoryRepository,
    private val soundRepository: SoundRepository,
    private val colorHelper: ColorHelper
) : ListAdapter<Int, CategoryAdapter.ViewHolder>(Comparator()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), activity, categoryRepository, soundRepository, colorHelper)
    }

    class ViewHolder(private val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        internal fun bind(
            categoryId: Int,
            activity: MainActivity,
            categoryRepository: CategoryRepository,
            soundRepository: SoundRepository,
            colorHelper: ColorHelper
        ) {
            val viewModel = ViewModelProvider(
                activity.viewModelStore,
                CategoryViewModel.Factory(categoryRepository, colorHelper, categoryId)
            )[categoryId.toString(), CategoryViewModel::class.java]

            binding.lifecycleOwner = activity
            binding.viewModel = viewModel

            binding.categoryCollapseButton.setOnClickListener { viewModel.toggleCollapsed() }
            binding.categoryDeleteButton.setOnClickListener { activity.showCategoryDeleteFragment(categoryId) }
            binding.categoryEditButton.setOnClickListener { activity.showCategoryEditFragment(categoryId) }

            val soundAdapter = SoundAdapter(activity, activity.viewModelStore, soundRepository, colorHelper)
            binding.soundList.adapter = soundAdapter
            viewModel.soundIds.observe(activity) {
                soundAdapter.submitList(it)
            }
        }
    }

    class Comparator : DiffUtil.ItemCallback<Int>() {
        override fun areItemsTheSame(oldItem: Int, newItem: Int) = oldItem == newItem
        override fun areContentsTheSame(oldItem: Int, newItem: Int) = oldItem == newItem
    }
}
