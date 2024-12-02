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

class SoundAdapter(private val activity: MainActivity) :
    LifecycleListAdapter<Int, SoundAdapter.ViewHolder>(Comparator()), LoggingObject {

    override fun getItemId(position: Int) = getItem(position).toLong()

    override fun getItemViewType(position: Int) = R.id.soundContainer

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemSoundBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        activity,
    )

    class ViewHolder(private val binding: ItemSoundBinding, private val activity: MainActivity) :
        LoggingObject,
        View.OnLongClickListener,
        View.OnClickListener,
        PlayerEventListener,
        LifecycleViewHolder(binding.root) {
        override val lifecycleRegistry = LifecycleRegistry(this)

        private val clickAnimator = ObjectAnimator.ofFloat(binding.soundCardBorder, "alpha", 0f)
        private val progressAnimator = ObjectAnimator().apply {
            interpolator = LinearInterpolator()
            target = binding.soundProgressBar
            setIntValues(0, 100)
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

        /** OVERRIDDEN METHODS ***********************************************/

        private fun addListeners() {
            viewModel?.setPlaybackEventListener(this)
        }

        override fun onAttach() {
            log("onAttach: soundId=$soundId")
            viewModel?.initPlayer()
            addListeners()
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

        override fun onDetach() {
            log("onDetach: soundId=$soundId")
            viewModel?.destroyPlayer()
            removeListeners()
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

        override fun onPermanentError(error: String) {
            playerPermanentError = error
        }

        override fun onPlaybackPaused() {
            pauseProgressAnimation()
        }

        override fun onPlaybackStarted() {
            startProgressAnimation()
        }

        override fun onPlaybackStopped() {
            stopProgressAnimation()
        }

        override fun onTemporaryError(error: String) {
            if (BuildConfig.DEBUG) activity.setSnackbarText(error)
        }

        /** PRIVATE/INTERNAL METHODS *****************************************/

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
                    }

                    viewModel.isSelectEnabled.observe(this) { isSelectEnabled = it }

                    viewModel.duration.observe(this) { progressAnimator.duration = it }

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

        private fun pauseProgressAnimation() = activity.runOnUiThread {
            if (viewModel?.isAnimationEnabled == true) {
                progressAnimator.pause()
            }
        }

        private fun removeListeners() {
            viewModel?.removePlaybackEventListener()
        }

        private fun startProgressAnimation() = activity.runOnUiThread {
            if (viewModel?.isAnimationEnabled == true) {
                if (progressAnimator.isPaused) progressAnimator.resume()
                else progressAnimator.start()
            }
        }

        private fun stopProgressAnimation() = activity.runOnUiThread {
            if (viewModel?.isAnimationEnabled == true) {
                progressAnimator.cancel()
                binding.soundProgressBar.progress = volume
            }
        }
    }

    class Comparator : DiffUtil.ItemCallback<Int>() {
        override fun areItemsTheSame(oldItem: Int, newItem: Int) = oldItem == newItem
        override fun areContentsTheSame(oldItem: Int, newItem: Int) = oldItem == newItem
    }
}