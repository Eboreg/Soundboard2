package us.huseli.soundboard2.ui

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.view.doOnLayout
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.Enums.RepressMode
import us.huseli.soundboard2.databinding.ItemSoundBinding
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.helpers.SoundPlayer
import us.huseli.soundboard2.viewmodels.SoundViewModel
import kotlin.math.roundToInt

class SoundAdapter(private val activity: MainActivity) :
    ListAdapter<Int, SoundAdapter.ViewHolder>(Comparator()), LoggingObject {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemSoundBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        activity,
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemSoundBinding,
        private val activity: MainActivity,
    ) : LoggingObject, View.OnLongClickListener, View.OnClickListener, RecyclerView.ViewHolder(binding.root) {
        private val clickAnimator = ObjectAnimator.ofFloat(binding.soundCardBorder, "alpha", 0f)
        private val progressAnimator = ObjectAnimator().apply {
            this.interpolator = LinearInterpolator()
            target = binding.soundProgressBar
            setPropertyName("progress")
        }

        var name = ""

        private var playerState: SoundPlayer.State? = null
        private var repressMode: RepressMode? = null
        private var isSelectEnabled = false
        private var isSelected = false
        private var isAnimationEnabled = false
        private var playerPermanentError = ""
        private var viewModel: SoundViewModel? = null
        private var volume = Constants.DEFAULT_VOLUME

        init {
            binding.lifecycleOwner = activity
            binding.root.setOnLongClickListener(this)
            binding.root.setOnClickListener(this)
        }

        internal fun bind(soundId: Int) {
            if (viewModel == null) {
                val localViewModel = ViewModelProvider(
                    activity.viewModelStore,
                    activity.defaultViewModelProviderFactory,
                    activity.defaultViewModelCreationExtras
                )["sound-$soundId", SoundViewModel::class.java]

                viewModel = localViewModel
                binding.viewModel = localViewModel

                localViewModel.repressMode.observe(activity) {
                    repressMode = it
                    // If changing to anything but PAUSE, make sure any paused sounds are stopped:
                    if (it != RepressMode.PAUSE) localViewModel.stopPaused()
                    // If changing to anything but OVERLAP, destroy any existing parallel players:
                    if (it != RepressMode.OVERLAP) localViewModel.destroyParallelPlayers()
                }

                localViewModel.isAnimationEnabled.observe(activity) { isAnimationEnabled = it }
                localViewModel.isSelectEnabled.observe(activity) { isSelectEnabled = it }

                localViewModel.playerState.observe(activity) {
                    if (it != playerState) {
                        log("playerState.observe: new playState=$it, was=$playerState")
                        playerState = it
                        when (it) {
                            SoundPlayer.State.STARTED -> startProgressAnimation()
                            SoundPlayer.State.PAUSED -> pauseProgressAnimation()
                            else -> stopProgressAnimation()
                        }
                    }
                }

                localViewModel.isSelected.observe(activity) {
                    isSelected = it
                }
                localViewModel.playerPermanentError.observe(activity) { error ->
                    if (error != null) playerPermanentError = error
                }
                localViewModel.playerTemporaryError.observe(activity) { error ->
                    if (error != null) activity.showSnackbar(error)
                }
                localViewModel.volume.observe(activity) {
                    if (!progressAnimator.isPaused && !progressAnimator.isStarted) binding.soundProgressBar.progress =
                        it
                    volume = it
                }
                localViewModel.name.observe(activity) {
                    name = it
                }

                binding.root.doOnLayout {
                    localViewModel.scrollEndSignal.observe(activity) {
                        val screenLocation = IntArray(2)
                        binding.root.getLocationOnScreen(screenLocation)
                        val (locationX, locationY) = screenLocation

                        if (localViewModel.screenHeightPx > 0) {
                            if (locationY + binding.root.height < 0 || locationY > localViewModel.screenHeightPx) {
                                // View is offscreen; reset player if needed.
                                localViewModel.schedulePlayerReset()
                            } else {
                                // View is onscreen; init player if needed.
                                localViewModel.schedulePlayerInit()
                            }
                        }
                        log("name=$name received scrollEndSignal; screenLocation=($locationX, $locationY), height=${binding.root.height}, bottom=${locationY + binding.root.height}, screenHeightPx=${localViewModel.screenHeightPx}, isLaidOut=${binding.root.isLaidOut}")
                    }
                }
            }

            viewModel?.setSoundId(soundId)
        }

        private fun animateClick() {
            if (isAnimationEnabled) {
                binding.soundCardBorder.alpha = 1f
                clickAnimator.start()
            }
        }

        private fun startProgressAnimation() {
            viewModel?.let { viewModel ->
                if (isAnimationEnabled) {
                    if (progressAnimator.isPaused) progressAnimator.resume()
                    else {
                        val currentPosition = viewModel.currentPosition
                        val duration = viewModel.duration
                        if (duration != null) {
                            val startPercent =
                                if (duration > 0) ((currentPosition.toDouble() / duration) * 100).roundToInt() else 0
                            progressAnimator.setIntValues(startPercent, 100)
                            progressAnimator.duration = (duration - currentPosition).toLong()
                            progressAnimator.start()
                        }
                    }
                }
            }
        }

        private fun stopProgressAnimation() {
            progressAnimator.cancel()
            binding.soundProgressBar.progress = volume
        }

        private fun pauseProgressAnimation() {
            progressAnimator.pause()
        }

        override fun onLongClick(v: View?): Boolean {
            viewModel?.let { viewModel ->
                animateClick()
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
            if (isSelectEnabled) {
                if (isSelected) viewModel?.unselect()
                else viewModel?.select()
            } else if (repressMode == RepressMode.OVERLAP) {
                viewModel?.playParallel()
            } else {
                when (playerState) {
                    SoundPlayer.State.IDLE -> viewModel?.play()
                    SoundPlayer.State.STARTED -> when (repressMode) {
                        RepressMode.STOP -> viewModel?.stop()
                        RepressMode.RESTART -> viewModel?.restart()
                        RepressMode.PAUSE -> viewModel?.pause()
                        else -> {}
                    }
                    SoundPlayer.State.PAUSED -> viewModel?.play()
                    SoundPlayer.State.ERROR -> activity.showSnackbar(playerPermanentError)
                    null -> {}
                }
            }
            animateClick()
        }
    }

    class Comparator : DiffUtil.ItemCallback<Int>() {
        override fun areItemsTheSame(oldItem: Int, newItem: Int) = oldItem == newItem
        override fun areContentsTheSame(oldItem: Int, newItem: Int) = oldItem == newItem
    }
}