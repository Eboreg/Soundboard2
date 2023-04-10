package us.huseli.soundboard2.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import us.huseli.soundboard2.R
import us.huseli.soundboard2.data.entities.Category

class CategorySpinnerAdapter(context: Context, objects: List<Category>) :
    ArrayAdapter<Category>(context, R.layout.category_spinner_dropdown_item, R.id.categorySpinnerItemText, objects) {

    private fun setItemColor(view: View, position: Int) {
        getItem(position)?.let { category ->
            val drawable = view.findViewById<ImageView>(R.id.categorySpinnerItemColor)?.drawable
            if (drawable is GradientDrawable) drawable.setColor(category.backgroundColor)
        }
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
        super.getDropDownView(position, convertView, parent).also { setItemColor(it, position) }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup) =
        super.getView(position, convertView, parent).also { setItemColor(it, position) }
}