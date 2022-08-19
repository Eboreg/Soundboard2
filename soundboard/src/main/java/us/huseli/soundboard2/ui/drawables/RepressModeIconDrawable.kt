package us.huseli.soundboard2.ui.drawables

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import org.xmlpull.v1.XmlPullParser
import us.huseli.fontawesome.FontAwesomeCache
import us.huseli.fontawesome.R
import us.huseli.soundboard2.Enums.RepressMode
import us.huseli.soundboard2.helpers.LoggingObject

class RepressModeIconDrawable : LoggingObject, MenuIconDrawable() {
    enum class CaretType { UP, DOWN }

    override val squareLayout = true
    override val rootAttributes = listOf(
        Attribute.TEXT_STYLE,
        Attribute.TEXT_SIZE,
        Attribute.TEXT_COLOR,
    )

    private var caretType = CaretType.DOWN
    private var typefaceSolid: Typeface? = null
    private var textCaretUp: CharSequence = ""
    private var textCaretDown: CharSequence = ""
    private val modeTexts = mutableMapOf<RepressMode, CharSequence>()
    private val textCaret: CharSequence
        get() = if (caretType == CaretType.UP) textCaretUp else textCaretDown
    private var repressMode: RepressMode? = null
    private var textLayoutCaret: StaticLayout? = null

    override fun inflate(r: Resources, parser: XmlPullParser, set: AttributeSet, theme: Resources.Theme?) {
        super.inflate(r, parser, set, theme)
        // It just so happens that all our icons are of the same typeface.
        typefaceSolid = FontAwesomeCache.get(r.assets, FontAwesomeCache.FA_FONT_SOLID)
        textPaint.typeface = typefaceSolid
        textCaretUp = r.getText(R.string.fas_caret_up)
        textCaretDown = r.getText(R.string.fas_caret_down)

        // Compile the string values and populate a map of them:
        RepressMode.values().forEach { mode ->
            val modeText = r.getText(
                when (mode) {
                    RepressMode.STOP -> R.string.fas_stop
                    RepressMode.RESTART -> R.string.fas_play
                    RepressMode.OVERLAP -> R.string.fas_clone
                    RepressMode.PAUSE -> R.string.fas_pause
                }
            )
            modeTexts[mode] = modeText
            // Set current icon if applicable:
            if (repressMode == mode) text = modeText
        }
    }

    override fun draw(canvas: Canvas) {
        val textLayout = this.textLayout
        val textLayoutCaret = this.textLayoutCaret

        if (textLayout != null && textLayoutCaret != null) {
            canvas.save()
            // Move canvas & draw repress mode icon:
            canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
            textLayout.draw(canvas)
            // Move canvas & draw caret:
            canvas.translate(
                bounds.left.toFloat() + textLayout.width.toFloat(),
                bounds.top.toFloat() + ((textLayout.height.toFloat() - textLayoutCaret.height.toFloat()) / 2)
            )
            textLayoutCaret.draw(canvas)
            canvas.restore()
        }
    }

    override fun measureContent() {
        val desired = getDesiredWidth()
        val textLayout = StaticLayout.Builder
            .obtain(text, 0, text.length, textPaint, desired)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()
        val textLayoutCaret = StaticLayout.Builder
            .obtain(textCaret, 0, textCaret.length, getCaretTextPaint(), desired)
            .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
            .setIncludePad(false)
            .build()

        this.textLayout = textLayout
        this.textLayoutCaret = textLayoutCaret
        textBounds.set(0, 0, textLayout.width + textLayoutCaret.width, textLayout.height)
        invalidateSelf()
    }

    fun setRepressMode(value: RepressMode) {
        repressMode = value
        modeTexts[value]?.let { text = it }
    }

    private fun getCaretTextPaint() = TextPaint(Paint.ANTI_ALIAS_FLAG).also {
        it.isDither = true
        it.color = textPaint.color
        it.typeface = typefaceSolid
        it.density = textPaint.density
        it.textSize = textPaint.textSize * 0.75f
        it.textScaleX = textPaint.textScaleX
        it.alpha = textPaint.alpha
        it.colorFilter = textPaint.colorFilter
    }

    fun setCaretType(value: CaretType) {
        caretType = value
        measureContent()
    }
}