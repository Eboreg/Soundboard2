package us.huseli.soundboard2.helpers

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter

abstract class LifecycleAdapter<T, VH : LifecycleViewHolder>(diffCallback: DiffUtil.ItemCallback<T>) : ListAdapter<T, VH>(diffCallback) {
    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.markCreated()
    }

    override fun onViewDetachedFromWindow(holder: VH) {
        super.onViewDetachedFromWindow(holder)
        holder.markDetach()
    }

    override fun onViewAttachedToWindow(holder: VH) {
        super.onViewAttachedToWindow(holder)
        holder.markAttach()
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        holder.markDestroyed()
    }
}