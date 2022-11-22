package us.huseli.soundboard2.ui.fragments

import androidx.annotation.StringRes
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import us.huseli.soundboard2.R
import us.huseli.soundboard2.viewmodels.CategoryEditViewModel

@AndroidEntryPoint
class CategoryEditFragment : BaseCategoryEditFragment() {
    override val viewModel by activityViewModels<CategoryEditViewModel>()
    @StringRes
    override val dialogTitle = R.string.edit_category
}