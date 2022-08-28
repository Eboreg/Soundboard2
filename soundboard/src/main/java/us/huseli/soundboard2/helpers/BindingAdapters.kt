package us.huseli.soundboard2.helpers

import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.VectorDrawable
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.annotation.ColorInt
import androidx.databinding.BindingAdapter

@BindingAdapter("backgroundColor")
fun setBackground(view: View, @ColorInt color: Int?) {
    color?.let { view.setBackgroundColor(it) }
}

@BindingAdapter("progressTintColor")
fun setProgressTintColor(view: ProgressBar, @ColorInt color: Int?) {
    color?.let { view.progressTintList = ColorStateList.valueOf(color) }
}

@BindingAdapter("progressBackgroundTintColor")
fun setProgressBackgroundTintColor(view: ProgressBar, @ColorInt color: Int?) {
    color?.let { view.progressBackgroundTintList = ColorStateList.valueOf(color) }
}

@BindingAdapter("visible")
fun setVisible(view: View, value: Boolean?) {
    value?.let { view.visibility = if (it) View.VISIBLE else View.GONE }
}

@BindingAdapter("drawableColor")
fun setDrawableColor(view: ImageView, color: Int?) {
    if (color != null) {
        when (view.drawable) {
            is GradientDrawable -> (view.drawable as GradientDrawable).setColor(color)
            is VectorDrawable -> (view.drawable as VectorDrawable).apply {
                setTint(color)
                setTintMode(PorterDuff.Mode.SRC_IN)
            }
            else -> (view.drawable as Drawable).colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
    }
}
