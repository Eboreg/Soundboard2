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
        var soundAdapter: SoundAdapter? = null,
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
        val adapterCombos = getAdapterCombos()

        // 1. If adapters already exist for all the category ID's and no
        // others, and they are in the correct order: do nothing
        if (categoryIds == adapterCombos.map { it.categoryId }) return

        // 2. Add adapters for any new category ID's
        categoryIds.minus(adapterCombos.map { it.categoryId }.toSet()).forEach { categoryId ->
            val combo = AdapterCombo(
                categoryId,
                CategoryHeaderAdapter(activity, categoryId).apply { setHasStableIds(true) },
                SoundAdapter(activity).apply { setHasStableIds(true) }
            )
            adapterCombos.add(combo)
        }

        // 3. Remove adapters for any old category ID's
        adapterCombos.map { it.categoryId }.minus(categoryIds.toSet()).forEach { categoryId ->
            adapterCombos.removeIf { it.categoryId == categoryId }
        }

        // 4. Reorder adapters if needed
        adapterCombos.sortBy { categoryIds.indexOf(it.categoryId) }

        // 5. Remove and re-add adapters from ConcatAdapter, because apparently
        // this is how we have to do it
        (adapter as? ConcatAdapter)?.let { concatAdapter ->
            concatAdapter.adapters.forEach { concatAdapter.removeAdapter(it) }
            adapterCombos.forEach { (_, headerAdapter, soundAdapter) ->
                concatAdapter.addAdapter(headerAdapter)
                soundAdapter?.let { concatAdapter.addAdapter(it) }
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
        var currentCombo: AdapterCombo? = null
        (adapter as? ConcatAdapter)?.let { concatAdapter ->
            concatAdapter.adapters.forEach { adapter ->
                when (adapter) {
                    is CategoryHeaderAdapter ->
                        currentCombo = AdapterCombo(adapter.categoryId, adapter).also { combos.add(it) }
                    is SoundAdapter -> currentCombo?.let { it.soundAdapter = adapter }
                }
            }
        }
        return combos
    }
}