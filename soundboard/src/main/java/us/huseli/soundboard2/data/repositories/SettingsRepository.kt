package us.huseli.soundboard2.data.repositories

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import us.huseli.soundboard2.Constants
import us.huseli.soundboard2.Enums
import us.huseli.soundboard2.Enums.RepressMode
import us.huseli.soundboard2.data.dao.CategoryDao
import us.huseli.soundboard2.data.entities.Category
import us.huseli.soundboard2.helpers.LoggingObject
import us.huseli.soundboard2.helpers.UriDeserializer
import us.huseli.soundboard2.helpers.UriSerializer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Suppress("BooleanMethodIsAlwaysInverted")
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    categoryDao: CategoryDao
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

    private inner class JsonFields {
        val isAnimationEnabled = _isAnimationEnabled
        val isWatchFolderEnabled = _isWatchFolderEnabled
        val watchFolderUri = _watchFolderUri
        val watchFolderTrashMissing = _watchFolderTrashMissing
        val spanCountPortrait = _spanCountPortrait.value
        val repressMode = _repressMode.value
        val watchFolderCategoryId = _watchFolderCategoryId.value
    }

    private val _gson: Gson = GsonBuilder()
        .registerTypeAdapter(Uri::class.java, UriSerializer())
        .registerTypeAdapter(Uri::class.java, UriDeserializer())
        .create()

    private val _preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private var _isAnimationEnabled = _preferences.getBoolean("isAnimationEnabled", true)
    private var _isWatchFolderEnabled = _preferences.getBoolean("isWatchFolderEnabled", false)
    private var _watchFolderUri = _preferences.getString("watchFolderUri", null)?.let { Uri.parse(it) }
    private var _watchFolderTrashMissing = _preferences.getBoolean("watchFolderTrashMissing", false)

    private val _spanCountPortrait =
        MutableStateFlow(_preferences.getInt("spanCountPortrait", Constants.DEFAULT_SPANCOUNT_PORTRAIT))
    private val _spanCountLandscape = MutableStateFlow(
        portraitSpanCountToLandscape(
            _preferences.getInt("spanCountPortrait", Constants.DEFAULT_SPANCOUNT_PORTRAIT)
        )
    )
    private val _orientation = MutableStateFlow(Enums.Orientation.PORTRAIT)
    private val _repressMode = MutableStateFlow(
        _preferences.getString("repressMode", null)
            ?.let { RepressMode.valueOf(it) } ?: RepressMode.STOP
    )
    private val _watchFolderCategoryId = MutableStateFlow(
        _preferences.getInt("watchFolderCategoryId", -1).let { if (it == -1) null else it }
    )
    private val _soundFilterTerm = MutableStateFlow("")

    val spanCount: Flow<Int> = combine(_orientation, _spanCountPortrait) { orientation, spanCountPortrait ->
        if (orientation == Enums.Orientation.PORTRAIT) spanCountPortrait
        else portraitSpanCountToLandscape(spanCountPortrait)
    }

    val repressMode: StateFlow<RepressMode> = _repressMode.asStateFlow()
    val isZoomInPossible: Flow<Boolean> = spanCount.map { it > 1 }
    val isAnimationEnabled: Boolean
        get() = _isAnimationEnabled
    val isWatchFolderEnabled: Boolean
        get() = _isWatchFolderEnabled
    val watchFolderUri: Uri?
        get() = _watchFolderUri
    val watchFolderCategory: Flow<Category?> =
        _watchFolderCategoryId.map { categoryId -> categoryId?.let { categoryDao.get(it) } }
    val watchFolderTrashMissing: Boolean
        get() = _watchFolderTrashMissing
    val soundFilterTerm: StateFlow<String> = _soundFilterTerm.asStateFlow()

    fun initialize() {
        _preferences.unregisterOnSharedPreferenceChangeListener(this)
        _preferences.registerOnSharedPreferenceChangeListener(this)
        _orientation.value =
            when (context.resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> Enums.Orientation.LANDSCAPE
                else -> Enums.Orientation.PORTRAIT
            }
    }

    /** ZOOMING & DIMENSIONS *************************************************/

    internal fun landscapeSpanCountToPortrait(spanCount: Int) = max((spanCount * getScreenRatio()).roundToInt(), 1)

    internal fun portraitSpanCountToLandscape(spanCount: Int) = max((spanCount / getScreenRatio()).roundToInt(), 1)

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

    /** META **************************************************************/

    fun dumpJson(): String = _gson.toJson(JsonFields())

    fun loadJson(json: String) {
        _gson.fromJson(json, JsonFields::class.java)?.also { fields ->
            setAnimationEnabled(fields.isAnimationEnabled)
            setWatchFolder(
                fields.isWatchFolderEnabled,
                fields.watchFolderUri,
                fields.watchFolderCategoryId,
                fields.watchFolderTrashMissing
            )
            _preferences.edit().putInt("spanCountPortrait", fields.spanCountPortrait).apply()
            setRepressMode(fields.repressMode)
        }
    }

    /** VARIOUS **************************************************************/

    fun setAnimationEnabled(value: Boolean) = _preferences.edit().putBoolean("isAnimationEnabled", value).apply()

    fun setRepressMode(value: RepressMode) = _preferences.edit().putString("repressMode", value.name).apply()

    fun setSoundFilterTerm(value: String) {
        _soundFilterTerm.value = value
    }

    fun setWatchFolder(enabled: Boolean, uri: Uri? = null, categoryId: Int? = null, trashMissing: Boolean? = null) {
        if (enabled)
            _preferences.edit()
                .putString("watchFolderUri", uri?.toString())
                .putInt("watchFolderCategoryId", categoryId ?: -1)
                .putBoolean("watchFolderTrashMissing", trashMissing ?: false)
                .putBoolean("isWatchFolderEnabled", true)  // Probably important to put this one last.
                .apply()
        else
            _preferences.edit().putBoolean("isWatchFolderEnabled", false).apply()
    }

    /** OVERRIDDEN METHODS ***************************************************/

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "spanCountPortrait" -> _preferences.getInt(key, Constants.DEFAULT_SPANCOUNT_PORTRAIT).also {
                _spanCountPortrait.value = it
                _spanCountLandscape.value = portraitSpanCountToLandscape(it)
            }
            "repressMode" -> _preferences.getString(key, null)
                .also { it?.let { _repressMode.value = RepressMode.valueOf(it) } }
            "isAnimationEnabled" -> _preferences.getBoolean(key, false).also { _isAnimationEnabled = it }
            "isWatchFolderEnabled" -> _preferences.getBoolean(key, false).also { _isWatchFolderEnabled = it }
            "watchFolderUri" -> _preferences.getString(key, null)
                .also { folder -> _watchFolderUri = folder?.let { Uri.parse(it) } }
            "watchFolderCategoryId" -> _preferences.getInt(key, -1)
                .also { _watchFolderCategoryId.value = if (it < 0) null else it }
            "watchFolderTrashMissing" -> _preferences.getBoolean(key, false)
                .also { _watchFolderTrashMissing = it }
        }
    }
}