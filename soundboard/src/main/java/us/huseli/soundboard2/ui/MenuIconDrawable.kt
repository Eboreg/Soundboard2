package us.huseli.soundboard2.ui

import android.content.res.Resources
import android.util.TypedValue
import us.huseli.fontawesome.FontAwesomeDrawable
import us.huseli.soundboard2.R

class MenuIconDrawable : FontAwesomeDrawable() {
    /** Without TEXT_COLOR! */
    override val themeAttributes = listOf(
        Attribute.TEXT_STYLE,
        Attribute.TEXT_SIZE,
    )

    override fun applyTheme(t: Resources.Theme) {
        val tv = TypedValue()
        t.resolveAttribute(R.attr.menuIconColor, tv, true)
        if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            setTextColor(tv.data)
        }
        else if (tv.type == TypedValue.TYPE_STRING) {
            // Should be a ColorStateList reference
            val csl = t.resources.getColorStateList(tv.resourceId, t)
            setTextColor(csl)
        }
        super.applyTheme(t)
    }
}