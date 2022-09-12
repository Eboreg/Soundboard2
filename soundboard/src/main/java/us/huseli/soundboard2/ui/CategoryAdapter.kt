package us.huseli.soundboard2.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.data.repositories.SettingsRepository
import us.huseli.soundboard2.data.repositories.SoundRepository
import us.huseli.soundboard2.databinding.ItemCategoryBinding
import us.huseli.soundboard2.helpers.ColorHelper
import us.huseli.soundboard2.helpers.LifecycleAdapter
import us.huseli.soundboard2.helpers.LifecycleViewHolder
import us.huseli.soundboard2.viewmodels.CategoryViewModel

class CategoryAdapter(
    private val activity: MainActivity,
    private val categoryRepository: CategoryRepository,
    private val soundRepository: SoundRepository,
    private val settingsRepository: SettingsRepository,
    private val colorHelper: ColorHelper
) : LifecycleAdapter<Int, CategoryAdapter.ViewHolder>(Comparator()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        holder.bind(getItem(position), activity, categoryRepository, soundRepository, settingsRepository, colorHelper)
    }

    class ViewHolder(val binding: ItemCategoryBinding) : LifecycleViewHolder(binding.root) {
        override val lifecycleRegistry = LifecycleRegistry(this)

        internal fun bind(
            categoryId: Int,
            activity: MainActivity,
            categoryRepository: CategoryRepository,
            soundRepository: SoundRepository,
            settingsRepository: SettingsRepository,
            colorHelper: ColorHelper
        ) {
            val viewModel = ViewModelProvider(
                activity.viewModelStore,
                CategoryViewModel.Factory(
                    categoryRepository,
                    soundRepository,
                    settingsRepository,
                    colorHelper,
                    categoryId
                )
            )[categoryId.toString(), CategoryViewModel::class.java]
            val soundAdapter = SoundAdapter(activity, soundRepository, settingsRepository, colorHelper)

            binding.lifecycleOwner = this
            binding.viewModel = viewModel
            binding.soundList.adapter = soundAdapter

            binding.categoryCollapseButton.setOnClickListener { viewModel.toggleCollapsed() }
            binding.categoryDeleteButton.setOnClickListener { activity.showCategoryDeleteFragment(categoryId) }
            binding.categoryEditButton.setOnClickListener { activity.showCategoryEditFragment(categoryId) }
            binding.categoryMoveDown.setOnClickListener { viewModel.moveDown() }
            binding.categoryMoveUp.setOnClickListener { viewModel.moveUp() }

            viewModel.soundIds.observe(this) { soundAdapter.submitList(it) }
            viewModel.spanCount.observe(this) {
                (binding.soundList.layoutManager as? GridLayoutManager)?.spanCount = it
            }
        }
    }

    class Comparator : DiffUtil.ItemCallback<Int>() {
        override fun areItemsTheSame(oldItem: Int, newItem: Int) = oldItem == newItem
        override fun areContentsTheSame(oldItem: Int, newItem: Int) = oldItem == newItem
    }
}
