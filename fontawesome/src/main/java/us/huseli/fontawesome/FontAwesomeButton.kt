package us.huseli.fontawesome

import android.content.Context
import android.util.AttributeSet
import android.view.View

class FontAwesomeButton(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
    FontAwesomeTextView(context, attrs, defStyleAttr) {
    init {
        /** Set ripple effect on click. */
        val arr = intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
        val a = context.obtainStyledAttributes(arr)
        for (i in 0 until a.indexCount) {
            when (val idx = a.getIndex(i)) {
                0 -> setBackgroundResource(a.getResourceId(idx, 0))
            }
        }
        a.recycle()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        isClickable = enabled
    }

    override fun getTextAlignment() = View.TEXT_ALIGNMENT_CENTER

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, R.style.FontAwesomeTextView)

    constructor(context: Context) : this(context, null)
}