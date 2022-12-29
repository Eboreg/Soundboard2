package us.huseli.soundboard2.ui

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import us.huseli.soundboard2.BuildConfig
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.Enums.RepressMode
import us.huseli.soundboard2.R
import us.huseli.soundboard2.databinding.ItemSoundBinding
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.helpers.PlayerEventListener
import us.huseli.soundboard2.helpers.SoundPlayer
import us.huseli.soundboard2.viewmodels.SoundViewModel
import kotlin.math.roundToInt

class SoundAdapter(private val activity: MainActivity) :
    LifecycleListAdapter<Int, SoundAdapter.ViewHolder>(Comparator()), LoggingObject {

    override fun getItemId(position: Int) = getItem(position).toLong()

    override fun getItemViewType(position: Int) = R.id.soundContainer

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemSoundBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        activity,
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemSoundBinding, private val activity: MainActivity) :
        LoggingObject,
        View.OnLongClickListener,
        View.OnClickListener,
        PlayerEventListener,
        LifecycleViewHolder(binding.root) {
        override val lifecycleRegistry = LifecycleRegistry(this)

        private val clickAnimator = ObjectAnimator.ofFloat(binding.soundCardBorder, "alpha", 0f)
        private val progressAnimator = ObjectAnimator().apply {
            this.interpolator = LinearInterpolator()
            target = binding.soundProgressBar
            setPropertyName("progress")
        }

        private var repressMode: RepressMode? = null
        private var isSelectEnabled = false
        private var playerPermanentError: String? = null
        private var soundId: Int? = null
        private var viewModel: SoundViewModel? = null
        private var volume = Constants.DEFAULT_VOLUME

        init {
            binding.lifecycleOwner = this
            binding.root.setOnLongClickListener(this)
            binding.root.setOnClickListener(this)
        }

        private fun addListeners() {
            viewModel?.setPlaybackEventListener(this)
        }

        private fun removeListeners() {
            viewModel?.removePlaybackEventListener()
        }

        override fun onDetach() {
            log("onDetach: soundId=$soundId")
            viewModel?.destroyPlayer()
            removeListeners()
        }

        override fun onAttach() {
            log("onAttach: soundId=$soundId")
            viewModel?.initPlayer()
            addListeners()
        }

        internal fun bind(soundId: Int) {
            if (this.soundId != soundId) {
                this.soundId = soundId

                viewModel = ViewModelProvider(
                    activity.viewModelStore,
                    activity.defaultViewModelProviderFactory,
                    activity.defaultViewModelCreationExtras
                )["sound-$soundId", SoundViewModel::class.java].also { viewModel ->
                    viewModel.setSoundId(soundId)
                    binding.viewModel = viewModel

                    viewModel.repressMode.observe(this) {
                        repressMode = it
                        // If changing to anything but PAUSE, make sure any paused sounds are stopped:
                        if (it != RepressMode.PAUSE) viewModel.stopPaused()
                        // If changing to anything but OVERLAP, destroy any existing parallel players:
                        if (it != RepressMode.OVERLAP) viewModel.destroyParallelPlayers()
                    }

                    viewModel.isSelectEnabled.observe(this) { isSelectEnabled = it }

                    viewModel.volume.observe(this) {
                        if (!progressAnimator.isPaused && !progressAnimator.isStarted)
                            binding.soundProgressBar.progress = it
                        volume = it
                    }
                }
            }
        }

        private fun animateClick() {
            if (viewModel?.isAnimationEnabled == true) {
                binding.soundCardBorder.alpha = 1f
                clickAnimator.start()
            }
        }

        private fun startProgressAnimation(currentPosition: Int, duration: Int) = activity.runOnUiThread {
            if (viewModel?.isAnimationEnabled == true) {
                val startPercent =
                    if (duration > 0) ((currentPosition.toDouble() / duration) * 100).roundToInt() else 0
                progressAnimator.setIntValues(startPercent, 100)
                progressAnimator.duration = (duration - currentPosition).toLong()
                progressAnimator.start()
            }
        }

        private fun stopProgressAnimation() = activity.runOnUiThread {
            if (viewModel?.isAnimationEnabled == true) {
                progressAnimator.cancel()
                binding.soundProgressBar.progress = volume
            }
        }

        private fun pauseProgressAnimation(currentPosition: Int, duration: Int) = activity.runOnUiThread {
            if (viewModel?.isAnimationEnabled == true) {
                progressAnimator.pause()
                val percent = if (duration > 0) ((currentPosition.toDouble() / duration) * 100).roundToInt() else 0
                binding.soundProgressBar.progress = percent
            }
        }

        override fun onLongClick(v: View?): Boolean {
            animateClick()
            viewModel?.let { viewModel ->
                if (!isSelectEnabled) {
                    // Select is not enabled; enable it and select sound.
                    viewModel.enableSelect()
                    viewModel.select()
                } else {
                    // Select is enabled; if this sound is not selected, select it
                    // and all between it and the last selected one (if any).
                    viewModel.selectAllFromLastSelected()
                }
            }
            return true
        }

        override fun onClick(v: View?) {
            if (isSelectEnabled) viewModel?.toggleSelect()
            else if (repressMode == RepressMode.OVERLAP) viewModel?.playParallel()
            else when (viewModel?.playerState) {
                SoundPlayer.State.IDLE -> viewModel?.play()
                SoundPlayer.State.STARTED -> when (repressMode) {
                    RepressMode.STOP -> viewModel?.stop()
                    RepressMode.RESTART -> viewModel?.restart()
                    RepressMode.PAUSE -> viewModel?.pause()
                    else -> {}
                }
                SoundPlayer.State.PAUSED -> viewModel?.play()
                SoundPlayer.State.ERROR -> playerPermanentError?.let { activity.setSnackbarText(it) }
                null -> {}
            }
            animateClick()
        }

        override fun onPlaybackStarted(currentPosition: Int, duration: Int) {
            startProgressAnimation(currentPosition, duration)
        }

        override fun onPlaybackStopped() {
            stopProgressAnimation()
        }

        override fun onPlaybackPaused(currentPosition: Int, duration: Int) {
            pauseProgressAnimation(currentPosition, duration)
        }

        override fun onTemporaryError(error: String) {
            if (BuildConfig.DEBUG) activity.setSnackbarText(error)
        }

        override fun onPermanentError(error: String) {
            playerPermanentError = error
        }
    }

    class Comparator : DiffUtil.ItemCallback<Int>() {
        override fun areItemsTheSame(oldItem: Int, newItem: Int) = oldItem == newItem
        override fun areContentsTheSame(oldItem: Int, newItem: Int) = oldItem == newItem
    }
}