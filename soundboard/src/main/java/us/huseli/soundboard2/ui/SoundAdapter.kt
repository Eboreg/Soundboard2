package us.huseli.soundboard2.ui

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import us.huseli.soundboard2.Enums.RepressMode
import us.huseli.soundboard2.data.repositories.SettingsRepository
import us.huseli.soundboard2.data.repositories.SoundRepository
import us.huseli.soundboard2.databinding.ItemSoundBinding
import us.huseli.soundboard2.helpers.*
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
        log("onViewAttachedToWindow: holder=$holder, viewModel.sound=${holder.soundId}")
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        log("onViewDetachedFromWindow: holder=$holder, viewModel.sound=${holder.soundId}")
    }

    override fun onFailedToRecycleView(holder: ViewHolder): Boolean {
        log("onFailedToRecycleView: holder=$holder, viewModel.sound=${holder.soundId}")
        return super.onFailedToRecycleView(holder)
    }


    class ViewHolder(private val binding: ItemSoundBinding) :
        LoggingObject,
        View.OnLongClickListener,
        View.OnClickListener,
        LifecycleViewHolder(binding.root)
    {
        override val lifecycleRegistry = LifecycleRegistry(this)
        private val animator = ObjectAnimator.ofFloat(binding.soundCardBorder, "alpha", 0f)

        private lateinit var activity: MainActivity
        private lateinit var viewModel: SoundViewModel
        private var playerState: SoundPlayer.State? = null
        private var repressMode: RepressMode? = null
        private var selectEnabled = false
        private var selected = false
        private var animationsEnabled = false
        private var playerPermanentError = ""
        internal var soundId: Int? = null

        internal fun bind(
            soundId: Int,
            activity: MainActivity,
            repository: SoundRepository,
            settingsRepository: SettingsRepository,
            colorHelper: ColorHelper
        ) {
            this.soundId = soundId
            this.activity = activity

            val viewModel = ViewModelProvider(
                activity.viewModelStore,
                SoundViewModel.Factory(repository, settingsRepository, colorHelper, soundId)
            )[soundId.toString(), SoundViewModel::class.java]

            this.viewModel = viewModel
            binding.lifecycleOwner = this
            binding.viewModel = viewModel

            binding.root.setOnLongClickListener(this)
            binding.root.setOnClickListener(this)

            viewModel.repressMode.observe(this) {
                repressMode = it
                // If changing to anything but PAUSE, make sure any paused sounds are stopped:
                if (it != RepressMode.PAUSE) viewModel.stopPaused()
                // If changing to anything but OVERLAP, destroy any existing parallel players:
                if (it != RepressMode.OVERLAP) viewModel.destroyParallelPlayers()
            }

            viewModel.animationsEnabled.observe(this) { animationsEnabled = it }
            viewModel.selectEnabled.observe(this) { selectEnabled = it }
            viewModel.playerState.observe(this) {
                if (it != playerState) {
                    log("playerState.observe: new playState=$it, was=$playerState")
                    playerState = it
                }
            }
            viewModel.selected.observe(this) { selected = it }
            viewModel.playerPermanentError.observe(this) { error ->
                if (error != null) playerPermanentError = error
            }
            viewModel.playerTemporaryError.observe(this) { error ->
                if (error != null) activity.showSnackbar(error)
            }
            viewModel.volume.observe(this) { viewModel.setPlayerVolume(it) }
            viewModel.path.observe(this) { viewModel.setPlayerPath(it) }
        }

        private fun animateClick() {
            if (animationsEnabled) {
                binding.soundCardBorder.alpha = 1f
                animator.start()
            }
        }

        override fun onLongClick(v: View?): Boolean {
            log("onLongClick: v=$v")
            animateClick()
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
            log("onClick: v=$v, selectEnabled=$selectEnabled, playState=$playerState")
            animateClick()
            if (selectEnabled) {
                if (selected) viewModel.unselect()
                else viewModel.select()
            } else if (repressMode == RepressMode.OVERLAP) {
                viewModel.playParallel()
            } else {
                when (playerState) {
                    SoundPlayer.State.IDLE -> viewModel.play()
                    SoundPlayer.State.STARTED -> when (repressMode) {
                        RepressMode.STOP -> viewModel.stop()
                        RepressMode.RESTART -> viewModel.restart()
                        RepressMode.PAUSE -> viewModel.pause()
                        else -> {}
                    }
                    SoundPlayer.State.PAUSED -> viewModel.play()
                    SoundPlayer.State.ERROR -> activity
                    null -> {}
                }
            }
        }

        override fun markCreated() {
            super.markCreated()
            log("markCreated: soundId=${soundId}")
        }

        override fun markAttach() {
            super.markAttach()
            log("markAttach: soundId=${soundId}")
        }

        override fun markDetach() {
            super.markDetach()
            log("markDetach: soundId=${soundId}")
        }

        override fun markDestroyed() {
            super.markDestroyed()
            log("markDestroyed: soundId=${soundId}")
        }
    }

    class Comparator : DiffUtil.ItemCallback<Int>() {
        override fun areItemsTheSame(oldItem: Int, newItem: Int) = oldItem == newItem
        override fun areContentsTheSame(oldItem: Int, newItem: Int) = oldItem == newItem
    }
}