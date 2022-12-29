package us.huseli.soundboard2.helpers

import androidx.annotation.StringRes

interface SnackbarTextListener {
    fun setSnackbarText(@StringRes resId: Int)
    fun setSnackbarText(text: CharSequence)
}