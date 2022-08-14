package us.huseli.fontawesome

import android.content.res.AssetManager
import android.graphics.Typeface
import java.util.*

object FontAwesomeCache {
    const val FA_FONT_REGULAR = "fa-regular-400.ttf"
    const val FA_FONT_SOLID = "fa-solid-900.ttf"
    const val FA_FONT_BRANDS = "fa-brands-400.ttf"

    private val fontCache = Hashtable<String, Typeface>()

    fun get(assets: AssetManager, name: String): Typeface =
        fontCache[name] ?: Typeface.createFromAsset(assets, name).also { fontCache[name] = it }
}