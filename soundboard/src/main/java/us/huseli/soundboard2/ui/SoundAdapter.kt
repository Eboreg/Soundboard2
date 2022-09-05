package us.huseli.soundboard2.ui

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
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
import us.huseli.soundboard2.data.entities.SoundExtended
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
) : ListAdapter<SoundExtended, SoundAdapter.ViewHolder>(Comparator()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemSoundBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), activity, soundRepository, settingsRepository, colorHelper)
    }

    class ViewHolder(private val binding: ItemSoundBinding) :
        LoggingObject,
        View.OnTouchListener,
        View.OnLongClickListener,
        View.OnClickListener,
        RecyclerView.ViewHolder(binding.root)
    {
        private lateinit var viewModel: SoundViewModel
        private var playState: PlayState? = null
        private var repressMode: RepressMode? = null
        private var selectEnabled = false
        private var selected = false
        private var animationsEnabled = false
        private val animator = ObjectAnimator.ofFloat(binding.soundCardBorder, "alpha", 0f)

        internal fun bind(
            sound: SoundExtended,
            activity: MainActivity,
            repository: SoundRepository,
            settingsRepository: SettingsRepository,
            colorHelper: ColorHelper
        ) {
            val viewModel = ViewModelProvider(
                activity.viewModelStore,
                SoundViewModel.Factory(repository, settingsRepository, colorHelper, sound)
            )[sound.id.toString(), SoundViewModel::class.java]

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

            viewModel.animationsEnabled.observe(activity) { animationsEnabled = it }
            viewModel.selectEnabled.observe(activity) { selectEnabled = it }
            viewModel.playState.observe(activity) {
                log("playState.observe: new playState=$it, was=$playState")
                playState = it
            }
            viewModel.selected.observe(activity) { selected = it }

            viewModel.playerError.observe(activity) { playerError ->
                if (playerError != null) activity.showSnackbar(playerError)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            /** This seems to work, but I don't know exactly why. */
            log("onTouch: v=$v, event=$event")
            if (animationsEnabled) {
                when (event?.actionMasked) {
                    MotionEvent.ACTION_DOWN -> binding.soundCardBorder.alpha = 1f
                    MotionEvent.ACTION_UP -> animator.start()
                }
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
    }

    class Comparator : DiffUtil.ItemCallback<SoundExtended>() {
        override fun areItemsTheSame(oldItem: SoundExtended, newItem: SoundExtended) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: SoundExtended, newItem: SoundExtended) = (
                oldItem.backgroundColor == newItem.backgroundColor &&
                        oldItem.volume == newItem.volume &&
                        oldItem.duration == newItem.duration &&
                        oldItem.order == newItem.order &&
                        oldItem.uri == newItem.uri &&
                        oldItem.name == newItem.name
                )
    }
}