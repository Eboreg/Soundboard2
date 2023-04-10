package us.huseli.soundboard2.helpers

import android.content.Context
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import us.huseli.soundboard2.R
import java.lang.Float.min
import kotlin.math.max

class ColorHelper(private val context: Context) {
    @ColorRes
    private val colorResources = arrayListOf(
        R.color.amber_500,
        R.color.black,
        R.color.blue_500,
        R.color.blue_grey_500,
        R.color.brown_500,
        R.color.cyan_500,
        R.color.deep_orange_500,
        R.color.deep_purple_500,
        R.color.green_500,
        R.color.grey_500,
        R.color.indigo_500,
        R.color.light_blue_500,
        R.color.light_green_500,
        R.color.lime_500,
        R.color.orange_500,
        R.color.pink_500,
        R.color.purple_500,
        R.color.red_500,
        R.color.teal_500,
        R.color.white,
        R.color.yellow_500
    )

    @ColorInt
    val colors = colorResources.map { context.getColor(it) }.sorted()

    /**
     * For a dark colour (luminance < 0.4), return a lighter shade by
     * decreasing saturation and increasing value.
     * For a light colour (luminande >= 0.4), return a darker shade by
     * increasing saturation and decreasing value.
     *
     * We will attempt to increase/decrease these properties by `diff` each.
     * But if that would push one of them over its limit, the other one will be
     * increased/decreased by a little more instead (but max `maxDiff`).
     */
    @ColorInt
    fun darkenOrBrighten(@ColorInt color: Int, diff: Float = 0.3f, maxDiff: Float = 0.5f): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return if (Color.luminance(color) >= 0.4) {
            // High luminance: return darker shade (increase saturation/hsv[1], decrease value/hsv[2])
            Color.HSVToColor(
                floatArrayOf(
                    hsv[0],
                    min(1f, hsv[1] + max(diff + max(diff - hsv[2], 0f), maxDiff)),
                    max(0f, hsv[2] - min(diff + max(hsv[1] - 1f + diff, 0f), maxDiff))
                )
            )
        } else {
            // Low luminance: return lighter shade (decrease saturation/hsv[1], increase value/hsv[2])
            Color.HSVToColor(
                floatArrayOf(
                    hsv[0],
                    max(0f, hsv[1] - min(diff + max(hsv[2] - 1f + diff, 0f), maxDiff)),
                    min(1f, hsv[2] + max(diff + max(diff - hsv[1], 0f), maxDiff)),
                )
            )
        }
    }

    @ColorInt
    fun getColorOnBackground(@ColorInt backgroundColor: Int) =
        context.getColor(if (Color.luminance(backgroundColor) >= 0.4) R.color.black else R.color.white)

    @ColorInt
    fun getRandomColor(@ColorInt exclude: Collection<Int> = emptyList()): Int {
        val included = colors.filter { it !in exclude.toSet() }
        return if (included.isNotEmpty()) included.random() else colors.random()
    }
}