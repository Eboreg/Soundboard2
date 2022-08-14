package us.huseli.soundboard2.ui

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SearchView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import us.huseli.soundboard2.BuildConfig
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.Functions
import us.huseli.soundboard2.R
import us.huseli.soundboard2.data.repositories.CategoryRepository
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
class MainActivity : AppCompatActivity(), ColorPickerDialogListener, LoggingObject {
    private lateinit var binding: ActivityMainBinding
    @Inject lateinit var categoryRepository: CategoryRepository
    @Inject lateinit var colorHelper: ColorHelper
    @Inject lateinit var soundRepository: SoundRepository
    private val appViewModel by viewModels<AppViewModel>()
    private val soundAddViewModel by viewModels<SoundAddViewModel>()
    private val categoryDeleteViewModel by viewModels<CategoryDeleteViewModel>()
    private val categoryEditViewModel by viewModels<CategoryEditViewModel>()
    private var categoryAdapter: CategoryAdapter? = null
    private val addSoundLauncher = registerForActivityResult(GetMultipleSounds()) { addSoundsFromUris(it) }

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

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.actionAddSound -> addSoundLauncher.launch("audio/*")
            R.id.actionAddCategory -> showFragment(CategoryAddFragment::class.java)
        }
        return true
    }

    override fun onColorSelected(dialogId: Int, color: Int) {
        val fragment = supportFragmentManager.findFragmentByTag(Constants.FRAGMENT_TAGS[dialogId]) as ColorPickerDialogListener
        log("onColorSelected: dialogId=$dialogId, color=$color, fragment=$fragment")
        fragment.onColorSelected(dialogId, color)
    }

    override fun onDialogDismissed(dialogId: Int) {}

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
        categoryAdapter = CategoryAdapter(
            this,
            categoryRepository,
            soundRepository,
            colorHelper
        ).also { categoryAdapter ->
            binding.categoryList.apply {
                adapter = categoryAdapter
                layoutManager?.isItemPrefetchEnabled = true
            }
            appViewModel.categoryIds.observe(this) { categoryAdapter.submitList(it) }
        }
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

    /** Making stuff focus/unfocus the way I like it in search box. */
    private fun initSearchAction(item: MenuItem, view: SearchView) {
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
}