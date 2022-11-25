package us.huseli.soundboard2.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import us.huseli.soundboard2.databinding.ItemCategoryBinding
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.viewmodels.CategoryViewModel

class CategoryAdapter(private val activity: MainActivity) : ListAdapter<Int, CategoryAdapter.ViewHolder>(Comparator()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        activity
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemCategoryBinding,
        private val activity: MainActivity,
    ) : LoggingObject, RecyclerView.ViewHolder(binding.root) {
        private val soundAdapter = SoundAdapter(activity)
        private var viewModel: CategoryViewModel? = null

        init {
            binding.lifecycleOwner = activity
            binding.soundList.adapter = soundAdapter
            binding.soundList.setItemViewCacheSize(20)
        }

        internal fun bind(categoryId: Int) {
            if (viewModel == null) {
                val localViewModel = ViewModelProvider(
                    activity.viewModelStore,
                    activity.defaultViewModelProviderFactory,
                    activity.defaultViewModelCreationExtras
                )["category-$categoryId", CategoryViewModel::class.java]

                viewModel = localViewModel

                binding.viewModel = localViewModel
                binding.categoryCollapseButton.setOnClickListener { localViewModel.toggleCollapsed() }
                binding.categoryMoveDown.setOnClickListener { localViewModel.moveDown() }
                binding.categoryMoveUp.setOnClickListener { localViewModel.moveUp() }

                localViewModel.soundIds.observe(activity) { soundAdapter.submitList(it) }
                localViewModel.spanCount.observe(activity) {
                    (binding.soundList.layoutManager as? GridLayoutManager)?.spanCount = it
                }
            }

            viewModel?.setCategoryId(categoryId)

            binding.categoryDeleteButton.setOnClickListener { activity.showCategoryDeleteFragment(categoryId) }
            binding.categoryEditButton.setOnClickListener { activity.showCategoryEditFragment(categoryId) }
        }
    }

    class Comparator : DiffUtil.ItemCallback<Int>() {
        override fun areItemsTheSame(oldItem: Int, newItem: Int) = oldItem == newItem
        override fun areContentsTheSame(oldItem: Int, newItem: Int) = oldItem == newItem
    }
}
