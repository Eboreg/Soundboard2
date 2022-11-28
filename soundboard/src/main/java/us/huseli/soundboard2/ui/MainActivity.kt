package us.huseli.soundboard2.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.*
import android.widget.ArrayAdapter
import android.widget.SearchView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import us.huseli.soundboard2.BuildConfig
import us.huseli.soundboard2.Enums.RepressMode
import us.huseli.soundboard2.Functions
import us.huseli.soundboard2.R
import us.huseli.soundboard2.data.repositories.SettingsRepository
import us.huseli.soundboard2.databinding.ActivityMainBinding
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.helpers.MediaPlayerTests
import us.huseli.soundboard2.ui.fragments.*
import us.huseli.soundboard2.viewmodels.*
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), ColorPickerDialogListener, LoggingObject {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    private lateinit var binding: ActivityMainBinding

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

    private var actionMode: ActionMode? = null
    private var isWatchFolderEnabled: Boolean = false
    private var soundFilterTerm = ""
    private var watchFolderTrashMissing: Boolean = false

    private inner class AddMultipleSounds : ActivityResultContracts.GetMultipleContents() {
        override fun createIntent(context: Context, input: String): Intent {
            this@MainActivity.overridePendingTransition(0, 0)
            return super.createIntent(context, input).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private inner class SoundActionModeCallback : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
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
            appViewModel.unselectAllSounds()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val (screenWidth, screenHeight) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Pair(
                windowManager.currentWindowMetrics.bounds.right,
                windowManager.currentWindowMetrics.bounds.bottom
            )
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
            Pair(metrics.widthPixels, metrics.heightPixels)
        }
        appViewModel.setScreenSizePx(screenWidth, screenHeight)

        binding = ActivityMainBinding.inflate(layoutInflater)
        binding.debug = false
        setContentView(binding.root)

        setSupportActionBar(binding.actionBar.actionbarToolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        initCategoryList()

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                appViewModel.snackbarText.collect { showSnackbar(it) }
            }
        }

        appViewModel.watchFolderTrashMissing.observe(this) { watchFolderTrashMissing = it }
        appViewModel.soundFilterTerm.observe(this) { soundFilterTerm = it }

        // If we are supposed to watch a folder, now is the time to sync it.
        appViewModel.isWatchFolderEnabled.observe(this) {
            isWatchFolderEnabled = it
            if (it) appViewModel.syncWatchFolder()
        }

        appViewModel.isSelectEnabled.observe(this) {
            if (it) actionMode = startSupportActionMode(soundActionModeCallback)
            else actionMode?.finish()
        }

        appViewModel.deleteOrphanSoundFiles()

        setupEasterEggClickListener()
        if (BuildConfig.DEBUG) mptInit()
    }

    override fun onColorSelected(dialogId: Int, @ColorInt color: Int) {
        /**
         * ColorPicker will only allow Activities to handle callbacks, so hwre
         * is where we figure out which fragment to delegate the event to, and
         * then delegate it.
         */
        val fragment = getFragmentByDialogId(dialogId) { it is ColorPickerDialogListener }
        (fragment as? ColorPickerDialogListener)?.onColorSelected(dialogId, color)
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

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        /** Flip caret icon when repress mode menu is opened. */
        if (menu is SubMenu && menu.item.itemId == R.id.actionRepressMode)
            menu.item.icon = ResourcesCompat.getDrawable(resources, R.drawable.repress_mode_icon_with_up_caret, theme)
        return super.onMenuOpened(featureId, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        /** User selected item from the "main menu" at the top. */
        when (item.itemId) {
            R.id.actionAddSound -> {
                if (isWatchFolderEnabled && watchFolderTrashMissing) {
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
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onPanelClosed(featureId: Int, menu: Menu) {
        /** Flip caret icon when repress mode menu is closed. */
        if (menu is SubMenu && menu.item.itemId == R.id.actionRepressMode)
            menu.item.icon = ResourcesCompat.getDrawable(resources, R.drawable.repress_mode_icon_with_down_caret, theme)
        super.onPanelClosed(featureId, menu)
    }

    override fun onStart() {
        super.onStart()
        settingsRepository.initialize()
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

    fun showSnackbar(text: CharSequence) {
        if (text.isNotEmpty()) Snackbar.make(binding.root, text, Snackbar.LENGTH_SHORT).show()
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
        val adapter = CategoryAdapter(this)
        binding.categoryList.adapter = adapter
        binding.categoryList.layoutManager?.isItemPrefetchEnabled = true

        appViewModel.categoryIds.observe(this) {
            binding.categoryList.setItemViewCacheSize(it.size)
            adapter.submitList(it)
            if (it.isEmpty()) appViewModel.createDefaultCategory()
        }

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

        binding.categoryList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                appViewModel.registerScrollEvent()
            }
        })
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
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                view.setQuery("", true)
                view.clearFocus()
                binding.actionBar.actionbarToolbar.menu.findItem(R.id.actionRepressMode)?.isVisible = true
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
        showSnackbar(getString(R.string.zoom_level_percent, zoomPercent))
    }

    internal fun zoomOut() {
        val zoomPercent = appViewModel.zoomOut()
        showSnackbar(getString(R.string.zoom_level_percent, zoomPercent))
    }


    /** MEDIA PLAYER TEST SHIT ***********************************************/

    private lateinit var mpt: MediaPlayerTests

    private fun mptInit() {
        mpt = MediaPlayerTests(applicationContext)

        binding.mediaPlayerTestSpinner.adapter = ArrayAdapter(
            applicationContext,
            android.R.layout.simple_spinner_item,
            MediaPlayerTests.Func.values()
        )
        binding.mediaPlayerTestRun.setOnClickListener {
            mpt.runFunc(binding.mediaPlayerTestSpinner.selectedItem as MediaPlayerTests.Func)
        }
        binding.mediaPlayerTestReset.setOnClickListener {
            mpt.release()
            mpt = MediaPlayerTests(applicationContext)
        }
        binding.mediaPlayerTestRunAll.setOnClickListener {
            mpt.release()
            mpt = MediaPlayerTests(applicationContext)
            mpt.runTests()
        }
        binding.mediaPlayerTestRunChain.setOnClickListener {
            mpt.release()
            mpt = MediaPlayerTests(applicationContext)
            mpt.runChainTests()
        }
    }
}