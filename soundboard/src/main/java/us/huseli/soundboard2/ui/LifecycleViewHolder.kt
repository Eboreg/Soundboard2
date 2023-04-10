package us.huseli.soundboard2.ui

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.recyclerview.widget.RecyclerView

@Suppress("EmptyMethod")
abstract class LifecycleViewHolder(view: View) : RecyclerView.ViewHolder(view), LifecycleOwner {
    internal abstract val lifecycleRegistry: LifecycleRegistry
    private var wasPaused = false

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    fun markAttach() {
        if (wasPaused) {
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            wasPaused = false
        } else
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
        onAttach()
    }

    fun markCreated() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        onCreated()
    }

    fun markDetach() {
        wasPaused = true
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        onDetach()
    }

    open fun onAttach() {}
    open fun onCreated() {}
    open fun onDetach() {}
}