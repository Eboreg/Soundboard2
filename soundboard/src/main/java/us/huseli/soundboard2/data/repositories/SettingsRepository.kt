package us.huseli.soundboard2.data.repositories

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.asLiveData
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.Enums
import us.huseli.soundboard2.Enums.RepressMode
import us.huseli.soundboard2.data.dao.CategoryDao
import us.huseli.soundboard2.helpers.LoggingObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Singleton
class SettingsRepository @Inject constructor(
    categoryDao: CategoryDao,
    @ApplicationContext private val context: Context
) : LoggingObject, SharedPreferences.OnSharedPreferenceChangeListener, DefaultLifecycleObserver {
    private inner class SpanCounts(factor: Int) {
        val portrait = when (_orientation.value) {
            Enums.Orientation.PORTRAIT -> max(_spanCountPortrait.value + factor, 1)
            Enums.Orientation.LANDSCAPE -> landscapeSpanCountToPortrait(_spanCountLandscape.value + factor)
        }
        val landscape = when (_orientation.value) {
            Enums.Orientation.PORTRAIT -> portraitSpanCountToLandscape(_spanCountPortrait.value + factor)
            Enums.Orientation.LANDSCAPE -> max(_spanCountLandscape.value + factor, 1)
        }
    }

    private val _preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val _screenRatio = MutableStateFlow(getScreenRatio())
    private val _spanCountPortrait = MutableStateFlow(
        _preferences.getInt("spanCountPortrait", Constants.DEFAULT_SPANCOUNT_PORTRAIT)
    )
    private val _spanCountLandscape = MutableStateFlow(portraitSpanCountToLandscape(Constants.DEFAULT_SPANCOUNT_PORTRAIT))
    private val _orientation = MutableStateFlow(Enums.Orientation.PORTRAIT)
    private val _repressMode = MutableStateFlow(
        _preferences.getString("repressMode", null)?.let { RepressMode.valueOf(it) } ?: RepressMode.STOP
    )
    private val _isSelectEnabled = MutableStateFlow<Boolean?>(null)
    private val _selectedSounds = MutableStateFlow<Set<Int>>(emptySet())
    private val _animationsEnabled = MutableStateFlow(true)
    private val _watchFolder = MutableStateFlow<String?>(null)
    private val _watchFolderCategoryId = MutableStateFlow<Int?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val spanCount: Flow<Int> = _orientation.flatMapLatest {
        if (it == Enums.Orientation.LANDSCAPE) _spanCountLandscape else _spanCountPortrait
    }

    val repressMode = _repressMode.asStateFlow()
    val isZoomInPossible: Flow<Boolean> = spanCount.map { it > 1 }
    val isSelectEnabled: Flow<Boolean> = _isSelectEnabled.filterNotNull()
    val selectedSounds = _selectedSounds.asStateFlow()
    val animationsEnabled = _animationsEnabled.asStateFlow()
    val watchFolder = _watchFolder.asStateFlow()
    val watchFolderCategoryId = _watchFolderCategoryId.asStateFlow()
    val watchFolderCategory = combine(_watchFolderCategoryId, categoryDao.flowList()) { categoryId, categories ->
        categories.firstOrNull { it.id == categoryId }
    }

    private fun landscapeSpanCountToPortrait(spanCount: Int) = max((spanCount * _screenRatio.value).roundToInt(), 1)

    private fun portraitSpanCountToLandscape(spanCount: Int) = max((spanCount / _screenRatio.value).roundToInt(), 1)

    private fun getScreenRatio(): Double {
        val width = context.resources.configuration.screenWidthDp.toDouble()
        val height = context.resources.configuration.screenHeightDp.toDouble()
        return min(height, width) / max(height, width)
    }

    private fun getZoomPercent(): Int {
        return when (_orientation.value) {
            Enums.Orientation.LANDSCAPE ->
                (portraitSpanCountToLandscape(Constants.DEFAULT_SPANCOUNT_PORTRAIT).toDouble() / _spanCountLandscape.value * 100).roundToInt()
            Enums.Orientation.PORTRAIT ->
                (Constants.DEFAULT_SPANCOUNT_PORTRAIT.toDouble() / _spanCountPortrait.value * 100).roundToInt()
        }
    }

    private fun zoom(factor: Int): Int {
        /**
         * `factor` tells us which value to add to the span count, so it will be negative for zooming in, positive for
         * zooming out. Will not do anything if factor is negative and span count is already 1.
         */
        val newSpanCounts = SpanCounts(factor)
        _spanCountPortrait.value = newSpanCounts.portrait
        _spanCountLandscape.value = newSpanCounts.landscape
        _preferences.edit().putInt("spanCountPortrait", newSpanCounts.portrait).apply()
        return getZoomPercent()
    }

    fun zoomIn() = zoom(-1)

    fun zoomOut() = zoom(1)

    fun setRepressMode(value: RepressMode) {
        _preferences.edit().putString("repressMode", value.name).apply()
    }

    fun enableSelect() {
        _isSelectEnabled.value = true
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun disableSelect() {
        _isSelectEnabled.value = false
        _selectedSounds.value = emptySet()
    }

    fun selectSound(soundId: Int) {
        _selectedSounds.value += soundId
    }

    fun unselectSound(soundId: Int) {
        _selectedSounds.value -= soundId
        if (_selectedSounds.value.isEmpty()) disableSelect()
    }

    fun enableAnimations() {
        _preferences.edit().putBoolean("animationsEnabled", true).apply()
    }

    fun disableAnimations() {
        _preferences.edit().putBoolean("animationsEnabled", false).apply()
    }

    fun setWatchFolder(value: String?) {
        _preferences.edit().putString("watchFolder", value).apply()
    }

    fun setWatchFolderCategoryId(value: Int?) {
        log("setWatchFolderCategoryId(): value=$value")
        _preferences.edit().putInt("watchFolderCategoryId", value ?: -1).apply()
    }

    /** OVERRIDDEN METHODS ***************************************************/

    override fun onResume(owner: LifecycleOwner) {
        log("onResume()")
        super.onResume(owner)
        _preferences.registerOnSharedPreferenceChangeListener(this)
        _orientation.value =
            when (context.resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> Enums.Orientation.LANDSCAPE
                else -> Enums.Orientation.PORTRAIT
            }
        _screenRatio.value = getScreenRatio()
    }

    override fun onPause(owner: LifecycleOwner) {
        log("onPause()")
        super.onPause(owner)
        _preferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        log("onSharedPreferenceChanged(): key=$key")

        when (key) {
            "spanCountPortrait" -> _preferences.getInt(key, Constants.DEFAULT_SPANCOUNT_PORTRAIT).also {
                _spanCountPortrait.value = it
                _spanCountLandscape.value = portraitSpanCountToLandscape(it)
            }
            "repressMode" -> _preferences.getString(key, null).also {
                it?.let { _repressMode.value = RepressMode.valueOf(it) }
            }
            "animationsEnabled" -> _preferences.getBoolean(key, false).also {
                log("onSharedPreferenceChanged(): animationsEnabled=$it")
                _animationsEnabled.value = it
            }
            "watchFolder" -> _preferences.getString(key, null).also {
                log("onSharedPreferenceChanged(): watchFolder=$it")
                _watchFolder.value = it
            }
            "watchFolderCategoryId" -> _preferences.getInt(key, -1).also {
                log("onSharedPreferenceChanged(): watchFolderCategoryId=$it")
                _watchFolderCategoryId.value = if (it < 0) null else it
            }
        }
    }
}