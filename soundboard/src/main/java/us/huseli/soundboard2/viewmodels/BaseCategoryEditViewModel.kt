package us.huseli.soundboard2.viewmodels

import androidx.lifecycle.LiveData

interface BaseCategoryEditViewModel {
    fun setBackgroundColor(color: Int)

    val backgroundColor: LiveData<Int>
}