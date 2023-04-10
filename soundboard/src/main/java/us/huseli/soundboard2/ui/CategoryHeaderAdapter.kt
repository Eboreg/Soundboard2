package us.huseli.soundboard2.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import us.huseli.soundboard2.R
import us.huseli.soundboard2.databinding.ItemCategoryBinding
import us.huseli.soundboard2.viewmodels.CategoryViewModel

class CategoryHeaderAdapter(private val activity: MainActivity, val categoryId: Int) :
    LifecycleAdapter<CategoryHeaderAdapter.ViewHolder>() {
    override val lifecycleRegistry = LifecycleRegistry(this)

    override fun getItemCount() = 1

    override fun getItemId(position: Int) = categoryId.toLong()

    override fun getItemViewType(position: Int) = R.id.categoryHeader

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(activity, categoryId)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    class ViewHolder(private val binding: ItemCategoryBinding) : LifecycleViewHolder(binding.root) {
        override val lifecycleRegistry = LifecycleRegistry(this)

        fun bind(activity: MainActivity, categoryId: Int) {
            val viewModel: CategoryViewModel = ViewModelProvider(
                activity.viewModelStore,
                activity.defaultViewModelProviderFactory,
                activity.defaultViewModelCreationExtras
            )["category-$categoryId", CategoryViewModel::class.java]

            viewModel.setCategoryId(categoryId)
            binding.lifecycleOwner = this
            binding.viewModel = viewModel
            binding.categoryCollapseButton.setOnClickListener { viewModel.toggleCollapsed() }
            binding.categoryMoveDown.setOnClickListener { viewModel.moveDown() }
            binding.categoryMoveUp.setOnClickListener { viewModel.moveUp() }
            binding.categoryDeleteButton.setOnClickListener { activity.showCategoryDeleteFragment(categoryId) }
            binding.categoryEditButton.setOnClickListener { activity.showCategoryEditFragment(categoryId) }
        }
    }
}