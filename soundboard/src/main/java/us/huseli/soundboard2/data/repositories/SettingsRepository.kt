package us.huseli.soundboard2.data.repositories

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.Enums
import us.huseli.soundboard2.Enums.RepressMode
import us.huseli.soundboard2.helpers.LoggingObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : LoggingObject, SharedPreferences.OnSharedPreferenceChangeListener {
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
    private val _spanCountPortrait =
        MutableStateFlow(_preferences.getInt("spanCountPortrait", Constants.DEFAULT_SPANCOUNT_PORTRAIT))
    private val _spanCountLandscape =
        MutableStateFlow(portraitSpanCountToLandscape(Constants.DEFAULT_SPANCOUNT_PORTRAIT))
    private val _orientation = MutableStateFlow(Enums.Orientation.PORTRAIT)
    private val _repressMode = MutableStateFlow(
        _preferences.getString("repressMode", null)
            ?.let { RepressMode.valueOf(it) } ?: RepressMode.STOP
    )
    private val _selectEnabled = MutableStateFlow<Boolean?>(null)
    private val _selectedSounds = MutableStateFlow<Set<Int>>(emptySet())
    private val _animationsEnabled = MutableStateFlow(_preferences.getBoolean("animationsEnabled", false))
    private val _watchFolderEnabled =
        MutableStateFlow(_preferences.getBoolean("watchFolderEnabled", false))
    private val _watchFolderUri =
        MutableStateFlow(_preferences.getString("watchFolderUri", null)?.let { Uri.parse(it) })
    private val _watchFolderCategoryId = MutableStateFlow(
        _preferences.getInt("watchFolderCategoryId", -1).let { if (it == -1) null else it }
    )
    private val _watchFolderTrashMissing =
        MutableStateFlow(_preferences.getBoolean("watchFolderTrashMissing", false))

    @OptIn(ExperimentalCoroutinesApi::class)
    val spanCount: Flow<Int> = _orientation.flatMapLatest {
        if (it == Enums.Orientation.LANDSCAPE) _spanCountLandscape else _spanCountPortrait
    }

    val repressMode: StateFlow<RepressMode> = _repressMode.asStateFlow()
    val isZoomInPossible: Flow<Boolean> = spanCount.map { it > 1 }
    val selectEnabled: Flow<Boolean> = _selectEnabled.filterNotNull()
    val selectedSounds: StateFlow<Set<Int>> = _selectedSounds.asStateFlow()
    val animationsEnabled: StateFlow<Boolean> = _animationsEnabled.asStateFlow()
    val watchFolderEnabled: StateFlow<Boolean> = _watchFolderEnabled.asStateFlow()
    val watchFolderUri: StateFlow<Uri?> = _watchFolderUri.asStateFlow()
    val watchFolderCategoryId: StateFlow<Int?> = _watchFolderCategoryId.asStateFlow()
    val watchFolderTrashMissing: StateFlow<Boolean> = _watchFolderTrashMissing.asStateFlow()

    fun initialize() {
        log("initialize()")
        _preferences.unregisterOnSharedPreferenceChangeListener(this)
        _preferences.registerOnSharedPreferenceChangeListener(this)
        _orientation.value =
            when (context.resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> Enums.Orientation.LANDSCAPE
                else -> Enums.Orientation.PORTRAIT
            }
        _screenRatio.value = getScreenRatio()
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

    fun setRepressMode(value: RepressMode) = _preferences.edit().putString("repressMode", value.name).apply()

    fun enableSelect() { _selectEnabled.value = true }

    @Suppress("MemberVisibilityCanBePrivate")
    fun disableSelect() {
        _selectEnabled.value = false
        _selectedSounds.value = emptySet()
    }

    fun selectSound(soundId: Int) { _selectedSounds.value += soundId }

    fun unselectSound(soundId: Int) {
        _selectedSounds.value -= soundId
        if (_selectedSounds.value.isEmpty()) disableSelect()
    }

    fun enableAnimations() = _preferences.edit().putBoolean("animationsEnabled", true).apply()

    fun disableAnimations() = _preferences.edit().putBoolean("animationsEnabled", false).apply()

    fun setWatchFolder(enabled: Boolean, uri: Uri? = null, categoryId: Int? = null, trashMissing: Boolean? = null) {
        if (enabled)
            _preferences.edit()
                .putString("watchFolderUri", uri?.toString())
                .putInt("watchFolderCategoryId", categoryId ?: -1)
                .putBoolean("watchFolderTrashMissing", trashMissing ?: false)
                .putBoolean("watchFolderEnabled", enabled)  // Probably important to put this one last.
                .apply()
        else
            _preferences.edit().putBoolean("watchFolderEnabled", enabled).apply()
    }

    /** OVERRIDDEN METHODS ***************************************************/

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        log("onSharedPreferenceChanged(): key=$key")

        when (key) {
            "spanCountPortrait" -> _preferences.getInt(key, Constants.DEFAULT_SPANCOUNT_PORTRAIT).also {
                _spanCountPortrait.value = it
                _spanCountLandscape.value = portraitSpanCountToLandscape(it)
            }
            "repressMode" -> _preferences.getString(key, null)
                .also { it?.let { _repressMode.value = RepressMode.valueOf(it) } }
            "animationsEnabled" -> _preferences.getBoolean(key, false).also { _animationsEnabled.value = it }
            "watchFolderEnabled" -> _preferences.getBoolean(key, false).also { _watchFolderEnabled.value = it }
            "watchFolderUri" -> _preferences.getString(key, null)
                .also { folder -> _watchFolderUri.value = folder?.let { Uri.parse(it) } }
            "watchFolderCategoryId" -> _preferences.getInt(key, -1)
                .also { _watchFolderCategoryId.value = if (it < 0) null else it }
            "watchFolderTrashMissing" -> _preferences.getBoolean(key, false)
                .also { _watchFolderTrashMissing.value = it }
        }
    }
}