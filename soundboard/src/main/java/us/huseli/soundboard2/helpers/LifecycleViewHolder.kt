package us.huseli.soundboard2.helpers

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.recyclerview.widget.RecyclerView

abstract class LifecycleViewHolder(view: View) : RecyclerView.ViewHolder(view), LifecycleOwner {
    internal abstract val lifecycleRegistry: LifecycleRegistry

    private var wasPaused = false

    open fun markCreated() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    open fun markAttach() {
        if (wasPaused) {
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            wasPaused = false
        }
        else lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    open fun markDetach() {
        wasPaused = true
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    open fun markDestroyed() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    override fun getLifecycle() = lifecycleRegistry
}