package us.huseli.soundboard2.ui

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import us.huseli.soundboard2.helpers.LoggingObject

class CategorySoundRecyclerView : RecyclerView, LoggingObject {
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context) : super(context)

    private data class AdapterCombo(
        val categoryId: Int,
        val headerAdapter: CategoryHeaderAdapter,
        var soundAdapter: SoundAdapter,
    )

    init {
        setHasFixedSize(true)
        adapter = ConcatAdapter(
            ConcatAdapter.Config.Builder()
                .setIsolateViewTypes(false)
                .setStableIdMode(ConcatAdapter.Config.StableIdMode.ISOLATED_STABLE_IDS)
                .build()
        )
        (layoutManager as? GridLayoutManager)?.let { gridLayoutManager ->
            gridLayoutManager.isItemPrefetchEnabled = true
            gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    (adapter as? ConcatAdapter)?.let { concatAdapter ->
                        val adapterAndPos = concatAdapter.getWrappedAdapterAndPosition(position)
                        return if (adapterAndPos.first is CategoryHeaderAdapter) gridLayoutManager.spanCount else 1
                    }
                    return 1
                }
            }
        }
    }

    fun setCategoryIds(activity: MainActivity, categoryIds: Collection<Int>) {
        val existingCombos = getAdapterCombos()
        val newCombos = mutableListOf<AdapterCombo>()

        // 1. If adapters already exist for all the category ID's and no
        // others, and they are in the correct order: do nothing
        if (categoryIds == existingCombos.map { it.categoryId }) return

        // 2. Create adapters for any new category ID's
        categoryIds.minus(existingCombos.map { it.categoryId }.toSet()).forEach { categoryId ->
            newCombos.add(
                AdapterCombo(
                    categoryId,
                    CategoryHeaderAdapter(activity, categoryId).apply { setHasStableIds(true) },
                    SoundAdapter(activity).apply { setHasStableIds(true) }
                )
            )
        }

        // 3. Remove adapters for any old category ID's
        (adapter as? ConcatAdapter)?.let { concatAdapter ->
            existingCombos.filterNot { it.categoryId in categoryIds }.forEach { (_, headerAdapter, soundAdapter) ->
                concatAdapter.removeAdapter(headerAdapter)
                concatAdapter.removeAdapter(soundAdapter)
            }
        }

        // 4. Insert new adapters in the right place and reorder existing
        (adapter as? ConcatAdapter)?.let { concatAdapter ->
            categoryIds.forEachIndexed { index, categoryId ->
                // If adapters for this category ID are already in the correct
                // place, do nothing:
                if ((concatAdapter.adapters.getOrNull(index * 2) as? CategoryHeaderAdapter)?.categoryId != categoryId) {
                    val existingCombo = existingCombos.firstOrNull { it.categoryId == categoryId }
                    val newCombo = newCombos.firstOrNull { it.categoryId == categoryId }

                    if (existingCombo != null) {
                        // Adapters exist but in the wrong place; move them.
                        concatAdapter.removeAdapter(existingCombo.headerAdapter)
                        concatAdapter.removeAdapter(existingCombo.soundAdapter)
                        concatAdapter.addAdapter(index * 2, existingCombo.headerAdapter)
                        concatAdapter.addAdapter(index * 2 + 1, existingCombo.soundAdapter)
                    } else if (newCombo != null) {
                        // Adapters do not exist; add them.
                        concatAdapter.addAdapter(index * 2, newCombo.headerAdapter)
                        concatAdapter.addAdapter(index * 2 + 1, newCombo.soundAdapter)
                    }
                }
            }
        }
    }

    fun setSoundIds(categoryId: Int, soundIds: Collection<Int>) {
        (adapter as? ConcatAdapter)?.let { concatAdapter ->
            concatAdapter.adapters.forEachIndexed { index, adapter ->
                if (adapter is CategoryHeaderAdapter && adapter.categoryId == categoryId) {
                    concatAdapter.adapters.getOrNull(index + 1)?.let {
                        if (it is SoundAdapter) {
                            log("ADAPTERDEBUG setSoundIds: categoryId=$categoryId, soundIds=$soundIds")
                            it.submitList(soundIds.toList())
                        }
                    }
                    return
                }
            }
        }
    }

    fun setSpanCount(spanCount: Int) {
        (layoutManager as? GridLayoutManager)?.spanCount = spanCount
    }

    private fun getAdapterCombos(): MutableList<AdapterCombo> {
        // We assume that a CategoryHeaderAdapter is always followed by a
        // SoundAdapter for that category.
        val combos = mutableListOf<AdapterCombo>()
        (adapter as? ConcatAdapter)?.let { concatAdapter ->
            concatAdapter.adapters.forEachIndexed { index, adapter ->
                if (adapter is CategoryHeaderAdapter) {
                    (concatAdapter.adapters.getOrNull(index + 1) as? SoundAdapter)?.let { soundAdapter ->
                        combos.add(AdapterCombo(adapter.categoryId, adapter, soundAdapter))
                    }
                }
            }
        }
        return combos
    }
}