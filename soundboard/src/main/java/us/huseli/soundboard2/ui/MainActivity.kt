package us.huseli.soundboard2.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.SearchView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.os.bundleOf
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DefaultItemAnimator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textview.MaterialTextView
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import us.huseli.soundboard2.Enums.RepressMode
import us.huseli.soundboard2.Functions
import us.huseli.soundboard2.R
import us.huseli.soundboard2.data.repositories.SettingsRepository
import us.huseli.soundboard2.databinding.ActivityMainBinding
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.helpers.SnackbarTextListener
import us.huseli.soundboard2.ui.fragments.*
import us.huseli.soundboard2.viewmodels.*
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), ColorPickerDialogListener, LoggingObject, SnackbarTextListener {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    internal val appViewModel by viewModels<AppViewModel>()
    internal val soundEditViewModel by viewModels<SoundEditViewModel>()

    private val actionbarLogoTouchTimes = mutableListOf<Long>()
    private val categoryAddViewModel by viewModels<CategoryAddViewModel>()
    private val categoryDeleteViewModel by viewModels<CategoryDeleteViewModel>()
    private val categoryEditViewModel by viewModels<CategoryEditViewModel>()
    private val soundAddViewModel by viewModels<SoundAddViewModel>()
    private val addSoundLauncher = registerForActivityResult(AddMultipleSounds()) { addSoundsFromUris(it) }
    private val scaleGestureDetector by lazy { ScaleGestureDetector(applicationContext, ScaleListener()) }
    private val soundActionModeCallback = SoundActionModeCallback()

    private lateinit var soundActionModeCustomView: MaterialTextView
    private lateinit var binding: ActivityMainBinding
    private var actionMode: ActionMode? = null
    private var soundFilterTerm = ""

    private inner class AddMultipleSounds : ActivityResultContracts.GetMultipleContents() {
        override fun createIntent(context: Context, input: String): Intent {
            this@MainActivity.overridePendingTransition(0, 0)
            return super.createIntent(context, input).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private inner class SoundActionModeCallback : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.customView = soundActionModeCustomView
            mode.menuInflater.inflate(R.menu.sound_actionmode_menu, menu)
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.selectAll -> appViewModel.selectAllSounds()
                R.id.edit -> {
                    soundEditViewModel.initialize()
                    showFragment(SoundEditFragment::class.java)
                }
                R.id.delete -> showFragment(SoundDeleteFragment::class.java)
            }
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
        override fun onDestroyActionMode(mode: ActionMode) {
            appViewModel.disableSelect()
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            return if (detector.scaleFactor <= 0.8) {
                zoomOut()
                true
            } else if (detector.scaleFactor >= 1.3) {
                zoomIn()
                true
            } else super.onScale(detector)
        }
    }


    /** OVERRIDDEN METHODS ***************************************************/

    override fun onColorSelected(dialogId: Int, @ColorInt color: Int) {
        /**
         * ColorPicker will only allow Activities to handle callbacks, so hwre
         * is where we figure out which fragment to delegate the event to, and
         * then delegate it.
         */
        val fragment = getFragmentByDialogId(dialogId) { it is ColorPickerDialogListener }
        (fragment as? ColorPickerDialogListener)?.onColorSelected(dialogId, color)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val isAnimationEnabled = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .getBoolean("isAnimationEnabled", true)
        if (isAnimationEnabled) setTheme(R.style.SoundboardTheme)
        else setTheme(R.style.SoundboardTheme_NoAnimation)

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        binding.lifecycleOwner = this
        setContentView(binding.root)
        soundActionModeCustomView = MaterialTextView(this)
        setSupportActionBar(binding.actionBar.actionbarToolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        initCategoryList()

        appViewModel.setSnackbarTextListener(this)
        appViewModel.soundFilterTerm.observe(this) { soundFilterTerm = it }

        appViewModel.isSelectEnabled.observe(this) {
            if (it) actionMode = startSupportActionMode(soundActionModeCallback)
            else actionMode?.finish()
        }

        appViewModel.selectedAndTotalSoundCount.observe(this) { (selected, total) ->
            soundActionModeCustomView.text = getString(R.string.selected_sounds, selected, total)
        }

        appViewModel.deleteOrphanSoundFiles()
        setupEasterEggClickListener()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        /** Inflates top menu */
        menuInflater.inflate(R.menu.appbar_menu, menu)

        // Init some listeners to make stuff focus/unfocus in a nice way for
        // the search box:
        menu.findItem(R.id.actionSearch)?.also { item ->
            val view = item.actionView
            if (view is SearchView) initSearchAction(item, view)
        }

        // The below ViewModel observers have to be defined here, because the
        // callbacks make no sense unless the menu already exists.
        appViewModel.isZoomInPossible.observe(this) {
            setMenuItemEnabled(menu.findItem(R.id.actionZoomIn), it)
        }

        appViewModel.isRedoPossible.observe(this) {
            setMenuItemEnabled(menu.findItem(R.id.actionRedo), it)
        }

        appViewModel.isUndoPossible.observe(this) {
            setMenuItemEnabled(menu.findItem(R.id.actionUndo), it)
        }

        appViewModel.repressMode.observe(this) {
            // Change to the appropriate icon when repress mode changes:
            (menu.findItem(R.id.actionRepressMode).icon as LayerDrawable)
                .findDrawableByLayerId(R.id.repress_mode_icon).level = RepressMode.values().indexOf(it)
            when (it) {
                RepressMode.STOP -> menu.findItem(R.id.actionRepressModeStop).isChecked = true
                RepressMode.RESTART -> menu.findItem(R.id.actionRepressModeRestart).isChecked = true
                RepressMode.OVERLAP -> menu.findItem(R.id.actionRepressModeOverlap).isChecked = true
                RepressMode.PAUSE -> menu.findItem(R.id.actionRepressModePause).isChecked = true
                null -> {}
            }
        }

        return true
    }

    override fun onDialogDismissed(dialogId: Int) {}

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        /** User selected item from the "main menu" at the top. */
        when (item.itemId) {
            R.id.actionAddSound -> {
                if (appViewModel.isWatchFolderEnabled && appViewModel.watchFolderTrashMissing) {
                    showFragment(
                        InfoDialogFragment::class.java,
                        bundleOf(Pair("message", getText(R.string.cannot_add_sounds)))
                    )
                } else addSoundLauncher.launch("audio/*")
            }
            R.id.actionAddCategory -> {
                categoryAddViewModel.initialize()
                showFragment(CategoryAddFragment::class.java)
            }
            R.id.actionZoomIn -> zoomIn()
            R.id.actionZoomOut -> zoomOut()
            R.id.actionRepressModeOverlap -> appViewModel.setRepressMode(RepressMode.OVERLAP)
            R.id.actionRepressModePause -> appViewModel.setRepressMode(RepressMode.PAUSE)
            R.id.actionRepressModeRestart -> appViewModel.setRepressMode(RepressMode.RESTART)
            R.id.actionRepressModeStop -> appViewModel.setRepressMode(RepressMode.STOP)
            R.id.actionSettings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                overridePendingTransition(0, 0)
            }
            R.id.actionUndo -> appViewModel.undo()
            R.id.actionRedo -> appViewModel.redo()
            R.id.actionDeleteOrphans -> appViewModel.deleteOrphanSoundObjects()
            R.id.actionStopAll -> appViewModel.stopAllSounds()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        settingsRepository.initialize()

        // If we are supposed to watch a folder, now is the time to sync it.
        if (appViewModel.isWatchFolderEnabled) appViewModel.syncWatchFolder()

        val themeResId: Int?
        if (!appViewModel.isAnimationEnabled) {
            binding.categoryList.itemAnimator = null
            themeResId = R.style.SoundboardTheme_NoAnimation
        } else {
            binding.categoryList.itemAnimator = DefaultItemAnimator()
            themeResId = R.style.SoundboardTheme
        }
        if (themeResId != appViewModel.themeResId) {
            log("Old theme=${appViewModel.themeResId}, new theme=$themeResId; run setTheme() & recreate()")
            recreate()
        }
        appViewModel.themeResId = themeResId
    }

    override fun setSnackbarText(resId: Int) {
        setSnackbarText(applicationContext.getText(resId))
    }

    override fun setSnackbarText(text: CharSequence) {
        if (text.isNotEmpty()) Snackbar.make(binding.root, text, Snackbar.LENGTH_SHORT).show()
    }


    /** PUBLIC METHODS *******************************************************/

    fun showCategoryDeleteFragment(categoryId: Int) {
        categoryDeleteViewModel.initialize(categoryId)
        showFragment(CategoryDeleteFragment::class.java)
    }

    fun showCategoryEditFragment(categoryId: Int) {
        categoryEditViewModel.initialize(categoryId)
        showFragment(CategoryEditFragment::class.java)
    }


    /** PRIVATE/INTERNAL METHODS *********************************************/

    private fun addSoundsFromUris(uris: Collection<Uri>) {
        /** Used when adding sounds from within app and sharing sounds from other apps */
        soundAddViewModel.initialize()
        lifecycleScope.launch(Dispatchers.Default) {
            soundAddViewModel.setSoundFiles(Functions.extractMetadata(applicationContext, uris))
        }
        showFragment(SoundAddFragment::class.java)
    }

    private fun getFragmentByDialogId(
        dialogId: Int,
        extraCond: ((fragment: BaseDialogFragment<*>) -> Boolean)?
    ): BaseDialogFragment<*>? {
        /**
         * If there is at least one BaseDialogFragment that has this dialogId and also passes the optional extraCond
         * test, return the latest added one. The fragment's dialogId will be set to a hash of its class name. This is
         * mainly so onColorSelected() will be able to delegate to the fragment that initiated the colour picker.
         */
        return supportFragmentManager.fragments.reversed()
            .filterIsInstance<BaseDialogFragment<*>>()
            .firstOrNull { it.dialogId == dialogId && (extraCond == null || extraCond.invoke(it)) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initCategoryList() {
        appViewModel.categorySoundMultimap.observe(this) { multimap ->
            binding.categoryList.setCategoryIds(this, multimap.keys.map { it.id })
            multimap.forEach { (category, sounds) ->
                binding.categoryList.setSoundIds(category.id, sounds.map { it.id })
            }
            binding.categoryList.setItemViewCacheSize(multimap.values.sumOf { it.size + 1 })
        }

        appViewModel.createDefaultCategoryIfNeeded()
        appViewModel.spanCount.observe(this) { binding.categoryList.setSpanCount(it) }

        binding.categoryList.setOnTouchListener { view, event ->
            /** Pinch to zoom */
            when (event.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    view.performClick()
                    return@setOnTouchListener false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 1) return@setOnTouchListener false
                }
            }
            return@setOnTouchListener scaleGestureDetector.onTouchEvent(event)
        }

        binding.categoryList.doOnLayout {
            // TODO: Not working
            if (binding.categoryList.measuredWidth > 0 && binding.categoryList.measuredHeight > 0) {
                log("doOnLayout: w=${binding.categoryList.measuredWidth}, h=${binding.categoryList.measuredHeight}")
                binding.progressCircle.visible = false
            }
        }
    }

    private fun initSearchAction(item: MenuItem, view: SearchView) {
        /** Making stuff focus/unfocus the way I like it in search box. */
        view.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                view.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText != null) appViewModel.setSoundFilterTerm(newText)
                return true
            }
        })

        item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                view.isIconified = false
                binding.actionBar.actionbarToolbar.menu.findItem(R.id.actionRepressMode)?.isVisible = false
                binding.actionBar.actionbarToolbar.menu.findItem(R.id.actionStopAll)?.isVisible = false
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                view.setQuery("", true)
                view.clearFocus()
                binding.actionBar.actionbarToolbar.menu.findItem(R.id.actionRepressMode)?.isVisible = true
                binding.actionBar.actionbarToolbar.menu.findItem(R.id.actionStopAll)?.isVisible = true
                return true
            }
        })

        if (soundFilterTerm.isNotEmpty()) {
            view.setQuery(soundFilterTerm, false)
            item.expandActionView()
        }
    }

    private fun setMenuItemEnabled(item: MenuItem, value: Boolean) {
        item.isEnabled = value
        item.icon?.alpha = if (value) 255 else 128
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupEasterEggClickListener() {
        binding.actionBar.actionbarLogo.isClickable = true
        binding.actionBar.actionbarLogo.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) actionbarLogoTouchTimes.add(event.eventTime)
            if (actionbarLogoTouchTimes.size == 3) {
                if (actionbarLogoTouchTimes.first() + 1000 >= event.eventTime)
                    showFragment(EasterEggFragment::class.java)
                actionbarLogoTouchTimes.clear()
            } else if (actionbarLogoTouchTimes.size > 3)
                actionbarLogoTouchTimes.clear()
            true
        }
    }

    internal fun showFragment(fragmentClass: Class<out Fragment>, args: Bundle? = null) {
        supportFragmentManager
            .beginTransaction()
            .add(fragmentClass, args, null)
            .commit()
    }

    internal fun zoomIn() {
        val zoomPercent = appViewModel.zoomIn()
        setSnackbarText(getString(R.string.zoom_level_percent, zoomPercent))
    }

    internal fun zoomOut() {
        val zoomPercent = appViewModel.zoomOut()
        setSnackbarText(getString(R.string.zoom_level_percent, zoomPercent))
    }
}