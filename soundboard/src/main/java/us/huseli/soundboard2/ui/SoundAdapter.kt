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
import us.huseli.soundboard2.data.repositories.SettingsRepository
import us.huseli.soundboard2.data.repositories.SoundRepository
import us.huseli.soundboard2.databinding.ItemSoundBinding
import us.huseli.soundboard2.helpers.ColorHelper
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.helpers.SoundPlayer
import us.huseli.soundboard2.viewmodels.SoundViewModel
import kotlin.math.roundToInt

class SoundAdapter(
    private val activity: MainActivity,
    private val soundRepository: SoundRepository,
    private val settingsRepository: SettingsRepository,
    private val colorHelper: ColorHelper
) : ListAdapter<Int, SoundAdapter.ViewHolder>(Comparator()), LoggingObject {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemSoundBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), activity, soundRepository, settingsRepository, colorHelper)
    }


    class ViewHolder(private val binding: ItemSoundBinding) :
        LoggingObject,
        View.OnLongClickListener,
        View.OnClickListener,
        RecyclerView.ViewHolder(binding.root) {
        private val clickAnimator = ObjectAnimator.ofFloat(binding.soundCardBorder, "alpha", 0f)
        private val progressAnimator = ObjectAnimator().apply {
            this.interpolator = LinearInterpolator()
            target = binding.soundProgressBar
            setPropertyName("progress")
        }

        lateinit var viewModel: SoundViewModel
        var name = ""

        private lateinit var activity: MainActivity
        private var playerState: SoundPlayer.State? = null
        private var repressMode: RepressMode? = null
        private var isSelectEnabled = false
        private var isSelected = false
        private var isAnimationEnabled = false
        private var playerPermanentError = ""
        private var volume = Constants.DEFAULT_VOLUME

        internal fun bind(
            soundId: Int,
            activity: MainActivity,
            repository: SoundRepository,
            settingsRepository: SettingsRepository,
            colorHelper: ColorHelper
        ) {
            val viewModel = ViewModelProvider(
                activity.viewModelStore,
                SoundViewModel.Factory(
                    repository,
                    settingsRepository,
                    colorHelper,
                    soundId,
                    activity.audioThreadHandler
                )
            )["sound-$soundId", SoundViewModel::class.java]

            this.activity = activity
            this.viewModel = viewModel

            binding.lifecycleOwner = activity
            binding.viewModel = viewModel

            binding.root.setOnLongClickListener(this)
            binding.root.setOnClickListener(this)

            viewModel.repressMode.observe(activity) {
                repressMode = it
                // If changing to anything but PAUSE, make sure any paused sounds are stopped:
                if (it != RepressMode.PAUSE) viewModel.stopPaused()
                // If changing to anything but OVERLAP, destroy any existing parallel players:
                if (it != RepressMode.OVERLAP) viewModel.destroyParallelPlayers()
            }

            viewModel.isAnimationEnabled.observe(activity) { isAnimationEnabled = it }
            viewModel.isSelectEnabled.observe(activity) { isSelectEnabled = it }
            viewModel.playerState.observe(activity) {
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
            viewModel.isSelected.observe(activity) {
                isSelected = it
            }
            viewModel.playerPermanentError.observe(activity) { error ->
                if (error != null) playerPermanentError = error
            }
            viewModel.playerTemporaryError.observe(activity) { error ->
                if (error != null) activity.showSnackbar(error)
            }
            viewModel.volume.observe(activity) {
                if (!progressAnimator.isPaused && !progressAnimator.isStarted) binding.soundProgressBar.progress = it
                volume = it
            }
            viewModel.name.observe(activity) {
                name = it
            }

            binding.root.doOnLayout {
                viewModel.scrollEndSignal.observe(activity) {
                    val screenLocation = IntArray(2)
                    binding.root.getLocationOnScreen(screenLocation)
                    val (locationX, locationY) = screenLocation

                    if (viewModel.screenHeightPx > 0) {
                        if (locationY + binding.root.height < 0 || locationY > viewModel.screenHeightPx) {
                            // View is offscreen; reset player if needed.
                            viewModel.schedulePlayerReset()
                        } else {
                            // View is onscreen; init player if needed.
                            viewModel.schedulePlayerInit()
                        }
                    }
                    log("name=$name received scrollEndSignal; screenLocation=($locationX, $locationY), height=${binding.root.height}, bottom=${locationY + binding.root.height}, screenHeightPx=${viewModel.screenHeightPx}, isLaidOut=${binding.root.isLaidOut}")
                }
            }
        }

        private fun animateClick() {
            if (isAnimationEnabled) {
                binding.soundCardBorder.alpha = 1f
                clickAnimator.start()
            }
        }

        private fun startProgressAnimation() {
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

        private fun stopProgressAnimation() {
            progressAnimator.cancel()
            binding.soundProgressBar.progress = volume
        }

        private fun pauseProgressAnimation() {
            progressAnimator.pause()
        }

        override fun onLongClick(v: View?): Boolean {
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
            return true
        }

        override fun onClick(v: View?) {
            if (isSelectEnabled) {
                if (isSelected) viewModel.unselect()
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