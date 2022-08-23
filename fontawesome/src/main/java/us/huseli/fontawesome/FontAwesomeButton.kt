package us.huseli.fontawesome

import android.content.Context
import android.util.AttributeSet
import android.view.View
import kotlin.math.roundToInt

class FontAwesomeButton(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : FontAwesomeTextView(context, attrs, defStyleAttr) {
    init {
        /** Set ripple effect on click. */
        val arr = intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
        val ta = context.obtainStyledAttributes(arr)
        val resId = ta.getResourceId(0, 0)
        ta.recycle()
        setBackgroundResource(resId)
    }

    override fun getTextAlignment(): Int {
        return View.TEXT_ALIGNMENT_CENTER
    }

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, R.style.FontAwesomeTextView)

    constructor(context: Context) : this(context, null)
}