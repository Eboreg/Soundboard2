package us.huseli.fontawesome

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.res.getResourceIdOrThrow
import androidx.core.widget.TextViewCompat

open class FontAwesomeTextView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
    AppCompatTextView(context, attrs, defStyleAttr) {
    init {
        val arr = context.theme.obtainStyledAttributes(attrs, R.styleable.FontAwesomeTextView, 0, 0)
        var computedTypeface: String? = null

        try {
            val resName = context.resources.getResourceEntryName(
                arr.getResourceIdOrThrow(R.styleable.FontAwesomeTextView_android_text)
            )
            computedTypeface = when {
                resName.startsWith("fas") -> FontAwesomeCache.FA_FONT_SOLID
                resName.startsWith("fab") -> FontAwesomeCache.FA_FONT_BRANDS
                else -> FontAwesomeCache.FA_FONT_REGULAR
            }
        } catch (e: Exception) {}

        val explicitTypeface: String? = when (arr.getInt(R.styleable.FontAwesomeTextView_fa_typeface, -1)) {
            0 -> FontAwesomeCache.FA_FONT_REGULAR
            1 -> FontAwesomeCache.FA_FONT_SOLID
            2 -> FontAwesomeCache.FA_FONT_BRANDS
            else -> null
        }

        typeface = FontAwesomeCache.get(context.assets, explicitTypeface ?: computedTypeface ?: FontAwesomeCache.FA_FONT_REGULAR)
    }

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, R.style.FontAwesomeTextView)

    constructor(context: Context) : this(context, null)

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            this,
            12,
            1000,
            1,
            TypedValue.COMPLEX_UNIT_SP
        )
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
    }
}