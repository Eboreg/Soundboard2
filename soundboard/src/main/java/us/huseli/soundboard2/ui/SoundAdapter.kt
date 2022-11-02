package us.huseli.soundboard2.ui

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import us.huseli.soundboard2.Enums.PlayState
import us.huseli.soundboard2.Enums.RepressMode
import us.huseli.soundboard2.data.repositories.SettingsRepository
import us.huseli.soundboard2.data.repositories.SoundRepository
import us.huseli.soundboard2.databinding.ItemSoundBinding
import us.huseli.soundboard2.helpers.ColorHelper
import us.huseli.soundboard2.helpers.LifecycleAdapter
import us.huseli.soundboard2.helpers.LifecycleViewHolder
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.viewmodels.SoundViewModel

class SoundAdapter(
    private val activity: MainActivity,
    private val soundRepository: SoundRepository,
    private val settingsRepository: SettingsRepository,
    private val colorHelper: ColorHelper
) : LifecycleAdapter<Int, SoundAdapter.ViewHolder>(Comparator()), LoggingObject {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemSoundBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), activity, soundRepository, settingsRepository, colorHelper)
        super.onBindViewHolder(holder, position)
    }

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        super.onViewAttachedToWindow(holder)
        log("onViewAttachedToWindow: holder=$holder, viewModel.sound=${holder.viewModel.soundId}")
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        log("onViewDetachedFromWindow: holder=$holder, viewModel.sound=${holder.viewModel.soundId}")
    }

    override fun onFailedToRecycleView(holder: ViewHolder): Boolean {
        log("onFailedToRecycleView: holder=$holder, viewModel.sound=${holder.viewModel.soundId}")
        return super.onFailedToRecycleView(holder)
    }


    class ViewHolder(private val binding: ItemSoundBinding) :
        LoggingObject,
        View.OnTouchListener,
        View.OnLongClickListener,
        View.OnClickListener,
        LifecycleViewHolder(binding.root)
    {
        override val lifecycleRegistry = LifecycleRegistry(this)
        private val animator = ObjectAnimator.ofFloat(binding.soundCardBorder, "alpha", 0f)

        internal lateinit var viewModel: SoundViewModel
        private var playState: PlayState? = null
        private var repressMode: RepressMode? = null
        private var selectEnabled = false
        private var selected = false
        private var animationsEnabled = false
        private var soundId: Int? = null

        internal fun bind(
            soundId: Int,
            activity: MainActivity,
            repository: SoundRepository,
            settingsRepository: SettingsRepository,
            colorHelper: ColorHelper
        ) {
            this.soundId = soundId

            val viewModel = ViewModelProvider(
                activity.viewModelStore,
                SoundViewModel.Factory(repository, settingsRepository, colorHelper, soundId)
            )[soundId.toString(), SoundViewModel::class.java]

            this.viewModel = viewModel
            binding.lifecycleOwner = this
            binding.viewModel = viewModel

            binding.root.setOnTouchListener(this)
            binding.root.setOnLongClickListener(this)
            binding.root.setOnClickListener(this)

            viewModel.repressMode.observe(this) {
                repressMode = it
                // If changing to anything but PAUSE, make sure any paused sounds are stopped.
                if (it != RepressMode.PAUSE) viewModel.stopPaused()
            }

            viewModel.animationsEnabled.observe(this) { animationsEnabled = it }
            viewModel.selectEnabled.observe(this) { selectEnabled = it }
            viewModel.playState.observe(this) {
                if (it != playState) {
                    log("playState.observe: new playState=$it, was=$playState")
                    playState = it
                }
            }
            viewModel.selected.observe(this) { selected = it }
            viewModel.playerError.observe(this) { playerError ->
                if (playerError != null) activity.showSnackbar(playerError)
            }
            viewModel.volume.observe(this) { viewModel.setPlayerVolume(it) }
            viewModel.path.observe(this) { viewModel.setPlayerPath(it) }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            /** This seems to work, but I don't know exactly why. */
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> if (animationsEnabled) binding.soundCardBorder.alpha = 1f
                MotionEvent.ACTION_UP -> if (animationsEnabled) animator.start()
            }
            return false
        }

        override fun onLongClick(v: View?): Boolean {
            log("onLongClick: v=$v")
            if (!selectEnabled) {
                // Select is not enabled; enable it and select sound.
                viewModel.enableSelect()
                viewModel.select()
            } else {
                // Select is enabled; if this sound is not selected, select it
                // and all between it and the last selected one (if any).
                viewModel.selectAllFromLastSelected()
            }
            return true
        }

        override fun onClick(v: View?) {
            log("onClick: v=$v, selectEnabled=$selectEnabled, playState=$playState")
            if (selectEnabled) {
                if (selected) viewModel.unselect()
                else viewModel.select()
            } else {
                when (playState) {
                    PlayState.IDLE -> viewModel.play()
                    PlayState.STARTED -> when (repressMode) {
                        RepressMode.STOP -> viewModel.stop()
                        RepressMode.RESTART -> viewModel.restart()
                        RepressMode.OVERLAP -> viewModel.play(true)
                        RepressMode.PAUSE -> viewModel.pause()
                        null -> {}
                    }
                    PlayState.PAUSED -> viewModel.play()
                    PlayState.ERROR -> viewModel.play()
                    null -> {}
                }
            }
        }

        override fun markCreated() {
            super.markCreated()
            log("markCreated: viewModel.sound=${viewModel.soundId}")
        }

        override fun markAttach() {
            super.markAttach()
            log("markAttach: viewModel.sound=${viewModel.soundId}")
        }

        override fun markDetach() {
            super.markDetach()
            log("markDetach: viewModel.sound=${viewModel.soundId}")
        }

        override fun markDestroyed() {
            super.markDestroyed()
            log("markDestroyed: viewModel.sound=${viewModel.soundId}")
        }
    }

    class Comparator : DiffUtil.ItemCallback<Int>() {
        override fun areItemsTheSame(oldItem: Int, newItem: Int) = oldItem == newItem
        override fun areContentsTheSame(oldItem: Int, newItem: Int) = oldItem == newItem
    }
}