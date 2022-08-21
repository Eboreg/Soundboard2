package us.huseli.soundboard2.ui

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.net.Uri
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import us.huseli.soundboard2.Enums.PlayState
import us.huseli.soundboard2.Enums.RepressMode
import us.huseli.soundboard2.data.repositories.SettingsRepository
import us.huseli.soundboard2.data.repositories.SoundRepository
import us.huseli.soundboard2.databinding.ItemSoundBinding
import us.huseli.soundboard2.helpers.ColorHelper
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.helpers.SoundPlayer
import us.huseli.soundboard2.viewmodels.SoundViewModel

class SoundAdapter(
    private val activity: MainActivity,
    private val soundRepository: SoundRepository,
    private val settingsRepository: SettingsRepository,
    private val colorHelper: ColorHelper
) : ListAdapter<Int, SoundAdapter.ViewHolder>(Comparator()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemSoundBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), activity, soundRepository, settingsRepository, colorHelper)
    }

    class ViewHolder(private val binding: ItemSoundBinding) :
        LoggingObject, View.OnTouchListener, View.OnLongClickListener, View.OnClickListener, RecyclerView.ViewHolder(binding.root) {
        private lateinit var viewModel: SoundViewModel
        private var playState: PlayState? = null
        private var repressMode: RepressMode? = null
        private var isSelectEnabled = false
        private var isSelected = false
        private var disableAnimations = false
        private val animator = ObjectAnimator.ofFloat(binding.soundCardBorder, "alpha", 0f)

        internal fun bind(
            soundId: Int,
            activity: MainActivity,
            repository: SoundRepository,
            settingsRepository: SettingsRepository,
            colorHelper: ColorHelper
        ) {
            val viewModel = ViewModelProvider(
                activity.viewModelStore,
                SoundViewModel.Factory(repository, settingsRepository, colorHelper, soundId)
            )[soundId.toString(), SoundViewModel::class.java]

            this.viewModel = viewModel
            binding.lifecycleOwner = activity
            binding.viewModel = viewModel

            binding.root.setOnTouchListener(this)
            binding.root.setOnLongClickListener(this)
            binding.root.setOnClickListener(this)

            viewModel.repressMode.observe(activity) {
                repressMode = it
                // If changing to anything but PAUSE, make sure any paused sounds are stopped.
                if (it != RepressMode.PAUSE) viewModel.stopPaused()
            }

            viewModel.disableAnimations.observe(activity) { disableAnimations = it }
            viewModel.isSelectEnabled.observe(activity) { isSelectEnabled = it }
            viewModel.playState.observe(activity) {
                log("playState.observe: new playState=$it, was=$playState")
                playState = it
            }
            viewModel.isSelected.observe(activity) { isSelected = it }

            viewModel.playerError.observe(activity) { playerError ->
                if (playerError != null) activity.showSnackbar(playerError)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            /** This seems to work, but I don't know exactly why. */
            log("onTouch: v=$v, event=$event")
            if (!disableAnimations) {
                when (event?.actionMasked) {
                    MotionEvent.ACTION_DOWN -> binding.soundCardBorder.alpha = 1f
                    MotionEvent.ACTION_UP -> animator.start()
                }
            }
            return false
        }

        override fun onLongClick(v: View?): Boolean {
            log("onLongClick: v=$v")
            if (!isSelectEnabled) {
                // Select is not enabled; enable it and select sound.
                viewModel.enableSelect()
                viewModel.select()
                return true
            }
            return false
        }

        override fun onClick(v: View?) {
            log("onClick: v=$v, isSelectEnabled=$isSelectEnabled, playState=$playState")
            if (isSelectEnabled) {
                if (isSelected) viewModel.unselect()
                else viewModel.select()
            }
            else {
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
    }

    class Comparator: DiffUtil.ItemCallback<Int>() {
        override fun areItemsTheSame(oldItem: Int, newItem: Int) = oldItem == newItem
        override fun areContentsTheSame(oldItem: Int, newItem: Int) = oldItem == newItem
    }
}