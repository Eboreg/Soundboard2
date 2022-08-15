package us.huseli.soundboard2.ui

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.SearchView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import us.huseli.soundboard2.BuildConfig
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.Functions
import us.huseli.soundboard2.R
import us.huseli.soundboard2.data.repositories.CategoryRepository
import us.huseli.soundboard2.data.repositories.SettingsRepository
import us.huseli.soundboard2.data.repositories.SoundRepository
import us.huseli.soundboard2.databinding.ActivityMainBinding
import us.huseli.soundboard2.helpers.ColorHelper
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.viewmodels.AppViewModel
import us.huseli.soundboard2.viewmodels.CategoryDeleteViewModel
import us.huseli.soundboard2.viewmodels.CategoryEditViewModel
import us.huseli.soundboard2.viewmodels.SoundAddViewModel
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), ColorPickerDialogListener, LoggingObject, View.OnTouchListener {
    private lateinit var binding: ActivityMainBinding
    @Inject lateinit var categoryRepository: CategoryRepository
    @Inject lateinit var colorHelper: ColorHelper
    @Inject lateinit var soundRepository: SoundRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    private val appViewModel by viewModels<AppViewModel>()
    private val soundAddViewModel by viewModels<SoundAddViewModel>()
    private val categoryDeleteViewModel by viewModels<CategoryDeleteViewModel>()
    private val categoryEditViewModel by viewModels<CategoryEditViewModel>()
    private val addSoundLauncher = registerForActivityResult(GetMultipleSounds()) { addSoundsFromUris(it) }
    private val scaleGestureDetector by lazy { ScaleGestureDetector(applicationContext, ScaleListener()) }

    class GetMultipleSounds : ActivityResultContracts.GetMultipleContents() {
        override fun createIntent(context: Context, input: String) =
            super.createIntent(context, input).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** OVERRIDDEN METHODS ***************************************************/

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.actionBar.actionbarToolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        appViewModel.repressMode.observe(this) {
            binding.actionBar.actionbarToolbar.menu?.findItem(R.id.actionRepressMode)?.icon?.level = it.index
        }

        lifecycle.addObserver(settingsRepository)

        initCategoryList()
        if (BuildConfig.DEBUG) initDebug()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        /** Inflates top menu */
        menuInflater.inflate(R.menu.appbar_menu, menu)

        menu.findItem(R.id.actionSearch)?.also { item ->
            val view = item.actionView
            if (view is SearchView) initSearchAction(item, view)
        }

        /** These have to be done here, because the callbacks require the menu to exist. */
        appViewModel.zoomInPossible.observe(this) {
            binding.actionBar.actionbarToolbar.menu.findItem(R.id.actionZoomIn).apply {
                isEnabled = it
                icon?.alpha = if (it) 255 else 128
            }
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.actionAddSound -> addSoundLauncher.launch("audio/*")
            R.id.actionAddCategory -> showFragment(CategoryAddFragment::class.java)
            R.id.actionZoomIn -> zoomIn()
            R.id.actionZoomOut -> zoomOut()
        }
        return true
    }

    override fun onColorSelected(dialogId: Int, color: Int) {
        val fragment = supportFragmentManager.findFragmentByTag(Constants.FRAGMENT_TAGS[dialogId]) as ColorPickerDialogListener
        log("onColorSelected: dialogId=$dialogId, color=$color, fragment=$fragment")
        fragment.onColorSelected(dialogId, color)
    }

    override fun onDialogDismissed(dialogId: Int) {}

    override fun onTouch(view: View?, event: MotionEvent?): Boolean {
        when (event?.actionMasked) {
            MotionEvent.ACTION_UP -> {
                view?.performClick()
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) return false
            }
        }
        return scaleGestureDetector.onTouchEvent(event)
    }

    /** PUBLIC METHODS *******************************************************/

    @Suppress("MemberVisibilityCanBePrivate")
    fun showFragment(fragmentClass: Class<out Fragment>, args: Bundle? = null) {
        supportFragmentManager
            .beginTransaction()
            .add(fragmentClass, args, fragmentClass.simpleName)
            .commit()
    }

    fun showCategoryDeleteFragment(categoryId: Int) {
        categoryDeleteViewModel.setCategoryId(categoryId)
        showFragment(CategoryDeleteFragment::class.java)
    }

    fun showCategoryEditFragment(categoryId: Int) {
        categoryEditViewModel.setCategoryId(categoryId)
        showFragment(CategoryEditFragment::class.java)
    }

    fun zoomIn() {
        val zoomPercent = appViewModel.zoomIn()
        showSnackbar(getString(R.string.zoom_level_percent, zoomPercent))
    }

    fun zoomOut() {
        val zoomPercent = appViewModel.zoomOut()
        showSnackbar(getString(R.string.zoom_level_percent, zoomPercent))
    }

    /** PRIVATE METHODS ******************************************************/

    private fun addSoundsFromUris(uris: List<Uri>) {
        /** Used when adding sounds from within app and sharing sounds from other apps */
        soundAddViewModel.reset()
        lifecycleScope.launch {
            val soundFiles = Functions.extractMetadata(applicationContext, uris)
            soundAddViewModel.setSoundFiles(soundFiles)
        }
        showFragment(SoundAddFragment::class.java)
    }

    private fun initCategoryList() {
        val adapter = CategoryAdapter(this, categoryRepository, soundRepository, settingsRepository, colorHelper)
        binding.categoryList.adapter = adapter
        binding.categoryList.layoutManager?.isItemPrefetchEnabled = true
        appViewModel.categoryIds.observe(this) { adapter.submitList(it) }
        appViewModel.spanCount.observe(this) {  }
    }

    @SuppressLint("SetTextI18n")
    private fun initDebug() {
        binding.debug.visibility = View.VISIBLE
        binding.debugText.text = "BuildConfig.BUILD_TYPE: ${BuildConfig.BUILD_TYPE}"
        binding.changeColor1.setOnClickListener {
            lifecycleScope.launch {
                categoryRepository.setBackgroundColor(1, colorHelper.getRandomColor())
            }
        }
        binding.changeColor2.setOnClickListener {
            lifecycleScope.launch {
                categoryRepository.setBackgroundColor(2, colorHelper.getRandomColor())
            }
        }
        binding.changeColor3.setOnClickListener {
            lifecycleScope.launch {
                categoryRepository.setBackgroundColor(3, colorHelper.getRandomColor())
            }
        }
        binding.addSoundAsset.setOnClickListener {
            lifecycleScope.launch {
                val resId = R.raw.rnrvals
                val uri = Uri.parse(
                    ContentResolver.SCHEME_ANDROID_RESOURCE +
                            "://" + resources.getResourcePackageName(resId) +
                            "/" + resources.getResourceTypeName(resId) +
                            "/" + resources.getResourceEntryName(resId)
                )
                soundRepository.create(uri, 100, 1)
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
                if (newText != null) appViewModel.setFilterTerm(newText)
                return true
            }
        })

        item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                view.isIconified = false
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                view.setQuery("", true)
                view.clearFocus()
                return true
            }
        })
    }

    private fun showSnackbar(text: CharSequence) = Snackbar.make(binding.root, text, Snackbar.LENGTH_SHORT).show()

    @Suppress("unused")
    private fun showSnackbar(textResource: Int) = showSnackbar(getText(textResource))


    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector?): Boolean {
            detector?.scaleFactor?.let { scaleFactor ->
                if (scaleFactor <= 0.8) {
                    zoomOut()
                    return true
                }
                else if (scaleFactor >= 1.3) {
                    zoomIn()
                    return true
                }
            }
            return super.onScale(detector)
        }
    }
}