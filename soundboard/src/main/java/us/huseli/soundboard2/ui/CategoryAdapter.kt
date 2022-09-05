package us.huseli.soundboard2.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.data.repositories.SettingsRepository
import us.huseli.soundboard2.data.repositories.SoundRepository
import us.huseli.soundboard2.databinding.ItemCategoryBinding
import us.huseli.soundboard2.helpers.ColorHelper
import us.huseli.soundboard2.viewmodels.CategoryViewModel

class CategoryAdapter(
    private val activity: MainActivity,
    private val categoryRepository: CategoryRepository,
    private val soundRepository: SoundRepository,
    private val settingsRepository: SettingsRepository,
    private val colorHelper: ColorHelper
) : ListAdapter<Category, CategoryAdapter.ViewHolder>(Comparator()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(activity, binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), categoryRepository, soundRepository, settingsRepository, colorHelper)
    }

    class ViewHolder(private val activity: MainActivity, val binding: ItemCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        internal fun bind(
            category: Category,
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
                    category
                )
            )[category.id.toString(), CategoryViewModel::class.java]
            val soundAdapter = SoundAdapter(activity, soundRepository, settingsRepository, colorHelper)

            binding.lifecycleOwner = activity
            binding.viewModel = viewModel
            binding.soundList.adapter = soundAdapter

            binding.categoryCollapseButton.setOnClickListener { viewModel.toggleCollapsed() }
            binding.categoryDeleteButton.setOnClickListener { activity.showCategoryDeleteFragment(category) }
            binding.categoryEditButton.setOnClickListener { activity.showCategoryEditFragment(category) }

            viewModel.sounds.observe(activity) {
                soundAdapter.submitList(it)
            }

            viewModel.spanCount.observe(activity) {
                (binding.soundList.layoutManager as? GridLayoutManager)?.spanCount = it
            }
        }
    }

    class Comparator : DiffUtil.ItemCallback<Category>() {
        override fun areItemsTheSame(oldItem: Category, newItem: Category) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Category, newItem: Category) = (
                oldItem.name == newItem.name &&
                        oldItem.backgroundColor == newItem.backgroundColor &&
                        oldItem.order == newItem.order &&
                        oldItem.collapsed == newItem.collapsed &&
                        oldItem.soundSorting == newItem.soundSorting
                )
    }
}
