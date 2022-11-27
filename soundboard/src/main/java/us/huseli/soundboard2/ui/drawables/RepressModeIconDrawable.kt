package us.huseli.soundboard2.ui.drawables

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import us.huseli.soundboard2.Enums.RepressMode
import us.huseli.soundboard2.R
import us.huseli.soundboard2.helpers.LoggingObject
import kotlin.math.roundToInt

class RepressModeIconDrawable : LoggingObject, Drawable() {
    enum class CaretType { UP, DOWN }

    private var caretType = CaretType.DOWN
    private var repressMode = RepressMode.STOP

    private val modeDrawables = mutableMapOf<RepressMode, Drawable?>()
    private var caretUp: Drawable? = null
    private var caretDown: Drawable? = null

    override fun inflate(r: Resources, parser: XmlPullParser, set: AttributeSet, theme: Resources.Theme?) {
        super.inflate(r, parser, set, theme)

        caretUp = ResourcesCompat.getDrawable(r, R.drawable.icon_caret_up, theme)
        caretDown = ResourcesCompat.getDrawable(r, R.drawable.icon_caret_down, theme)

        RepressMode.values().forEach { mode ->
            modeDrawables[mode] = ResourcesCompat.getDrawable(
                r,
                when (mode) {
                    RepressMode.STOP -> R.drawable.icon_stop
                    RepressMode.RESTART -> R.drawable.icon_play
                    RepressMode.OVERLAP -> R.drawable.icon_clone
                    RepressMode.PAUSE -> R.drawable.icon_pause
                },
                theme
            )
        }
    }

    override fun draw(canvas: Canvas) {
        val modeDrawable = modeDrawables[repressMode]
        val caret = if (caretType == CaretType.DOWN) caretDown else caretUp

        if (modeDrawable != null && caret != null && bounds.width() > 0 && bounds.height() > 0) {
            val modeDrawableRatio: Double = modeDrawable.intrinsicWidth.toDouble() / modeDrawable.intrinsicHeight
            val modeDrawableWidth: Int = (bounds.width().toDouble() / 2).roundToInt()
            val modeDrawableHeight: Int = (modeDrawableWidth / modeDrawableRatio).roundToInt()
            val modeDrawableBitmap = modeDrawable.toBitmap(modeDrawableWidth, modeDrawableHeight)

            val caretRatio = caret.intrinsicWidth.toDouble() / caret.intrinsicHeight
            val caretWidth = (bounds.width() * 0.4).toInt()
            val caretHeight = (caretWidth / caretRatio).roundToInt()
            val caretBitmap = caret.toBitmap(caretWidth, caretHeight)

            canvas.drawBitmap(
                modeDrawableBitmap,
                0f,
                ((bounds.height() / 2) - (modeDrawableHeight / 2)).toFloat(),
                null
            )

            canvas.drawBitmap(
                caretBitmap,
                (bounds.width() - caretWidth).toFloat(),
                ((bounds.height() / 2) - (caretHeight / 2)).toFloat(),
                null
            )
        }
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    // override fun getConstantState(): ConstantState? = null

    fun setRepressMode(value: RepressMode) {
        repressMode = value
    }

    fun setCaretType(value: CaretType) {
        caretType = value
    }
}