package us.huseli.soundboard2.viewmodels

import androidx.lifecycle.LiveData

interface BaseCategoryEditViewModel {
    val backgroundColor: LiveData<Int>

    fun setBackgroundColor(color: Int)
}