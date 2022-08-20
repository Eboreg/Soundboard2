package us.huseli.soundboard2.data.repositories

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.Enums
import us.huseli.soundboard2.Enums.RepressMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : SharedPreferences.OnSharedPreferenceChangeListener, DefaultLifecycleObserver {
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val spanCount: Flow<Int> = _orientation.flatMapLatest {
        if (it == Enums.Orientation.LANDSCAPE) _spanCountLandscape else _spanCountPortrait
    }

    val repressMode = _repressMode.asStateFlow()
    val isZoomInPossible = spanCount.map { it > 1 }
    val isSelectEnabled = _isSelectEnabled.filterNotNull()
    val selectedSounds = _selectedSounds.asStateFlow()

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

    /** OVERRIDDEN METHODS ***************************************************/

    override fun onResume(owner: LifecycleOwner) {
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
        super.onPause(owner)
        _preferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "spanCountPortrait" -> _preferences.getInt(key, Constants.DEFAULT_SPANCOUNT_PORTRAIT).also {
                _spanCountPortrait.value = it
                _spanCountLandscape.value = portraitSpanCountToLandscape(it)
            }
            "repressMode" -> _preferences.getString(key, null).also {
                it?.let { _repressMode.value = RepressMode.valueOf(it) }
            }
        }
    }
}