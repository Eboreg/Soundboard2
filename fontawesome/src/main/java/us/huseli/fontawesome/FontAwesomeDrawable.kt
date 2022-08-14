package us.huseli.fontawesome

import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import androidx.annotation.ColorInt
import androidx.core.content.res.getResourceIdOrThrow
import org.xmlpull.v1.XmlPullParser
import kotlin.math.ceil

open class FontAwesomeDrawable : Drawable() {
    /** Some debug stuff */
    private val stateMap = mapOf(
        Pair(android.R.attr.state_above_anchor, "state_above_anchor"),
        Pair(android.R.attr.state_accelerated, "state_accelerated"),
        Pair(android.R.attr.state_activated, "state_activated"),
        Pair(android.R.attr.state_active, "state_active"),
        Pair(android.R.attr.state_checkable, "state_checkable"),
        Pair(android.R.attr.state_checked, "state_checked"),
        Pair(android.R.attr.state_drag_can_accept, "state_drag_can_accept"),
        Pair(android.R.attr.state_drag_hovered, "state_drag_hovered"),
        Pair(android.R.attr.state_empty, "state_empty"),
        Pair(android.R.attr.state_enabled, "state_enabled"),
        Pair(android.R.attr.state_expanded, "state_expanded"),
        Pair(android.R.attr.state_first, "state_first"),
        Pair(android.R.attr.state_focused, "state_focused"),
        Pair(android.R.attr.state_hovered, "state_hovered"),
        Pair(android.R.attr.state_last, "state_last"),
        Pair(android.R.attr.state_middle, "state_middle"),
        Pair(android.R.attr.state_multiline, "state_multiline"),
        Pair(android.R.attr.state_pressed, "state_pressed"),
        Pair(android.R.attr.state_selected, "state_selected"),
        Pair(android.R.attr.state_single, "state_single"),
        Pair(android.R.attr.state_window_focused, "state_window_focused"),
    )
    private var textResName: String? = null

    @Suppress("unused")
    enum class Attribute(val value: Int) {
        TEXT_SIZE(android.R.attr.textSize),
        TEXT_STYLE(android.R.attr.textStyle),
        TEXT(android.R.attr.text),
        TEXT_COLOR(android.R.attr.textColor),
        TEXT_APPEARANCE(android.R.attr.textAppearance),
        TYPEFACE(R.styleable.FontAwesomeDrawable_fa_typeface)
    }

    /** Some overrideable defaults */
    protected open val rootAttributes = listOf(
        Attribute.TEXT,
        Attribute.TEXT_STYLE,
        Attribute.TEXT_SIZE,
        Attribute.TEXT_COLOR,
        Attribute.TYPEFACE,
    )

    protected open val themeAttributes = listOf(
        Attribute.TEXT_STYLE,
        Attribute.TEXT_SIZE,
        Attribute.TEXT_COLOR,
    )

    open var text: CharSequence = ""
        set(value) {
            field = value
            measureContent()
        }

    open var typeface: Typeface? = null
        set(value) {
            if (value != null && field != value) {
                field = value
                updateTextPaint { it.typeface = value }
            }
        }

    /**
     * Optional Path object on which to draw the text.  If this is set,
     * FontAwesomeDrawable cannot properly measure the bounds this drawable
     * will need. You must call [setBounds()][.setBounds] before applying this
     * FontAwesomeDrawable to any View.
     *
     * Calling this method with `null` will remove any Path currently attached.
     */
    open var textPath: Path? = null
        set(value) {
            if (field != value) {
                field = value
                measureContent()
            }
        }

    /**
     * Text alignment value. Should be set to one of:
     * [Layout.Alignment.ALIGN_NORMAL],
     * [Layout.Alignment.ALIGN_NORMAL],
     * [Layout.Alignment.ALIGN_OPPOSITE].
     */
    open var textAlignment = Layout.Alignment.ALIGN_NORMAL
        set(value) {
            if (field != value) {
                field = value
                measureContent()
            }
        }

    @ColorInt private val defaultColor = Color.BLACK
    protected open var textColors: ColorStateList = ColorStateList.valueOf(defaultColor)

    private val rootAttributeMap by lazy { rootAttributes.associateBy { it.value }.toSortedMap() }
    private val themeAttributeMap by lazy { themeAttributes.associateBy { it.value }.toSortedMap() }
    private val extractedAttributes = mutableSetOf<Attribute>()

    /** Paint to hold most drawing primitives for the text */
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).also {
        it.isDither = true
        it.color = textColors.getColorForState(state, defaultColor)
    }

    /** Container for the bounds to be reported to widgets */
    private var textBounds = Rect()

    /** Layout is used to measure and draw the text */
    private var textLayout: StaticLayout? = null

    /** 0 = normal, 1 = bold, 2 = italic, 3 = bold italic */
    open var typefaceStyle: Int = -1
        set(value) {
            if (value != field) {
                field = value
                if (value > 0) {
                    Typeface.create(typeface, value)?.also { typeface = it }
                    val need = value and (typeface?.style ?: 0).inv()
                    updateTextPaint {
                        it.isFakeBoldText = (need and Typeface.BOLD) != 0
                        it.textSkewX = if ((need and Typeface.ITALIC) != 0) -0.25f else 0f
                    }
                } else {
                    updateTextPaint {
                        it.isFakeBoldText = false
                        it.textSkewX = 0f
                    }
                }
            }
        }

    override fun inflate(r: Resources, parser: XmlPullParser, set: AttributeSet, theme: Resources.Theme?) {
        super.inflate(r, parser, set, theme)

        setDensity(r.displayMetrics.density)

        var computedTypeface: String? = null
        var explicitTypeface: String? = null
        var newTypefaceStyle = 0
        val attrs = rootAttributeMap.keys.toIntArray()
        val a = r.obtainAttributes(set, attrs)

        for (i in 0 until a.indexCount) {
            val idx = a.getIndex(i)
            val attr = rootAttributeMap[attrs[idx]]
            when (attr) {
                Attribute.TEXT_SIZE -> setRawTextSize(a.getDimensionPixelSize(idx, -1).toFloat())
                Attribute.TEXT_STYLE -> newTypefaceStyle = a.getInt(idx, newTypefaceStyle)
                Attribute.TEXT_COLOR -> a.getColorStateList(idx)?.also { setTextColor(it) }
                Attribute.TEXT -> {
                    try {
                        val resName = r.getResourceEntryName(a.getResourceIdOrThrow(idx))
                        textResName = resName
                        computedTypeface = when {
                            resName.startsWith("fas") -> FontAwesomeCache.FA_FONT_SOLID
                            resName.startsWith("fab") -> FontAwesomeCache.FA_FONT_BRANDS
                            else -> FontAwesomeCache.FA_FONT_REGULAR
                        }
                    } catch (e: Exception) {}
                    text = a.getText(idx)
                }
                Attribute.TYPEFACE -> {
                    explicitTypeface = when(a.getInt(idx, -1)) {
                        0 -> FontAwesomeCache.FA_FONT_REGULAR
                        1 -> FontAwesomeCache.FA_FONT_SOLID
                        2 -> FontAwesomeCache.FA_FONT_BRANDS
                        else -> null
                    }
                }
                else -> {}
            }
            attr?.let { extractedAttributes.add(attr) }
        }

        a.recycle()
        // This probably needs to be done regardless:
        typefaceStyle = newTypefaceStyle
        typeface = FontAwesomeCache.get(
            r.assets,
            explicitTypeface ?: computedTypeface ?: FontAwesomeCache.FA_FONT_REGULAR
        )
        if (theme != null) applyTheme(theme)
    }

    override fun applyTheme(t: Resources.Theme) {
        val attrs = themeAttributeMap.keys.toIntArray()
        val appearanceArr = t.obtainStyledAttributes(intArrayOf(android.R.attr.textAppearance))
        val appearanceId = if (appearanceArr.indexCount > 0) appearanceArr.getResourceId(0, 0) else 0
        appearanceArr.recycle()

        val a = t.obtainStyledAttributes(appearanceId, attrs)
        for (i in 0 until a.indexCount) {
            val idx = a.getIndex(i)
            val attr = themeAttributeMap[attrs[idx]]
            if (!extractedAttributes.contains(attr)) {
                when (attr) {
                    Attribute.TEXT_SIZE -> setRawTextSize(a.getDimensionPixelSize(idx, -1).toFloat())
                    Attribute.TEXT_STYLE -> typefaceStyle = a.getInt(idx, typefaceStyle)
                    Attribute.TEXT_COLOR -> a.getColorStateList(idx)?.also { setTextColor(it) }
                    else -> {}
                }
            }
        }
        a.recycle()
    }

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        textPath?.let {
            // Draw directly on the canvas using the supplied path
            canvas.drawTextOnPath(text.toString(), it, 0f, 0f, textPaint)
        } ?: run {
            // Allow the layout to draw the text
            textLayout?.draw(canvas)
        }
        canvas.restore()
    }

    private fun measureContent() {
        // If drawing to a path, we cannot measure intrinsic bounds;
        // we must rely on setBounds being called externally.
        if (textPath != null) {
            // Clear any previous measurement.
            textLayout = null
            textBounds.setEmpty()
        } else {
            // Measure text bounds.
            val desired = ceil(Layout.getDesiredWidth(text, textPaint).toDouble())
            textLayout = StaticLayout.Builder
                .obtain(text, 0, text.length, textPaint, desired.toInt())
                .setAlignment(textAlignment)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()
                .also {
                    textBounds.set(0, 0, it.width, it.height)
                }
        }
        invalidateSelf()
    }

    override fun canApplyTheme() = true

    @Suppress("MemberVisibilityCanBePrivate")
    fun setDensity(value: Float) {
        if (value != textPaint.density) updateTextPaint { it.density = value }
    }

    /**
     * textPaint needs updating in a bunch of places; always do it via this
     * method, so we make sure measureContent() is also run
     */
    private fun updateTextPaint(func: (TextPaint) -> Unit): Boolean {
        func(textPaint)
        measureContent()
        return true
    }

    /** Set the text size, in raw pixels. */
    private fun setRawTextSize(size: Float) {
        if (size > 0f && size != textPaint.textSize) updateTextPaint { it.textSize = size }
    }

    /** Return the horizontal stretch factor of the text. */
    @Suppress("unused")
    fun getTextScaleX() = textPaint.textScaleX

    /** Set the horizontal stretch factor of the text. */
    @Suppress("unused")
    fun setTextScaleX(size: Float) {
        if (size != textPaint.textScaleX) updateTextPaint { it.textScaleX = size }
    }

    /** Set a single text colour for all states. */
    fun setTextColor(@ColorInt color: Int) = setTextColor(ColorStateList.valueOf(color))

    /** Set the text colour as a state list. */
    fun setTextColor(colorStateList: ColorStateList) {
        textColors = colorStateList
        updateTextColors()
    }

    /** Internal method to apply the correct text colour based on the drawable's state. */
    private fun updateTextColors(): Boolean {
        val newColor = textColors.getColorForState(state, defaultColor)
        return if (textPaint.color != newColor) updateTextPaint { it.color = newColor } else false
    }

    /** Update the internal bounds in response to any external requests. */
    override fun onBoundsChange(bounds: Rect?) {
        if (bounds != null) textBounds = bounds
    }

    /** The drawable's ability to represent state is based on the text colour list set. */
    override fun isStateful() = textColors.isStateful

    /** Upon state changes, grab the correct text colour. */
    override fun onStateChange(state: IntArray?) = updateTextColors()

    /** Return the measured vertical bounds, or -1 if none. */
    override fun getIntrinsicHeight() = if (!textBounds.isEmpty) textBounds.bottom - textBounds.top else -1

    /** Return the measured horizontal bounds, or -1 if none. */
    override fun getIntrinsicWidth() = if (!textBounds.isEmpty) textBounds.right - textBounds.left else -1

    override fun setAlpha(alpha: Int) {
        if (textPaint.alpha != alpha) updateTextPaint { it.alpha = alpha }
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = textPaint.alpha

    override fun setColorFilter(cf: ColorFilter?) {
        if (textPaint.colorFilter != cf) updateTextPaint { it.colorFilter = cf }
    }
}