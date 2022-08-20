package us.huseli.soundboard2.ui

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.net.Uri
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
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
        private var uri: Uri? = null
        private var volume: Int = 100
        private var isSelectEnabled = false
        private var isSelected = false
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

            viewModel.uri.observe(activity) { uri = it }
            viewModel.volume.observe(activity) { if (it != null) volume = it }
            viewModel.isSelectEnabled.observe(activity) { isSelectEnabled = it }
            viewModel.playState.observe(activity) { playState = it }
            viewModel.isSelected.observe(activity) { isSelected = it }

            viewModel.playerError.observe(activity) { playerError ->
                if (playerError != null) activity.showSnackbar(playerError)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            /** This seems to work, but I don't know exactly why. */
            log("onTouch: v=$v, event=$event")
            when (event?.actionMasked) {
                MotionEvent.ACTION_DOWN -> binding.soundCardBorder.alpha = 1f
                MotionEvent.ACTION_UP -> animator.start()
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
            log("onClick: v=$v")
            if (isSelectEnabled) {
                if (isSelected) viewModel.unselect()
                else viewModel.select()
            }
            else {
                when (playState) {
                    PlayState.IDLE -> viewModel.play(uri?.path, volume)
                    PlayState.STARTED -> when (repressMode) {
                        RepressMode.STOP -> viewModel.stop()
                        RepressMode.RESTART -> viewModel.restart(uri?.path, volume)
                        RepressMode.OVERLAP -> viewModel.play(uri?.path, volume, true)
                        RepressMode.PAUSE -> viewModel.pause()
                        null -> {}
                    }
                    PlayState.PAUSED -> viewModel.play(uri?.path, volume)
                    PlayState.ERROR -> viewModel.play(uri?.path, volume)
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