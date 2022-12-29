package us.huseli.soundboard2.ui

import androidx.recyclerview.widget.RecyclerView

abstract class LifecycleAdapter<VH : LifecycleViewHolder> : RecyclerView.Adapter<VH>() {
    private val viewHolders = mutableListOf<VH>()

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        super.onBindViewHolder(holder, position, payloads)
        holder.markCreated()
        viewHolders.add(holder)
    }

    override fun onViewAttachedToWindow(holder: VH) {
        super.onViewAttachedToWindow(holder)
        holder.markAttach()
    }

    override fun onViewDetachedFromWindow(holder: VH) {
        super.onViewDetachedFromWindow(holder)
        holder.markDetach()
    }
}