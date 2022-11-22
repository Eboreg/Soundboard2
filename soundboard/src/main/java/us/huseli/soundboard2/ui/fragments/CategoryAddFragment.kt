package us.huseli.soundboard2.ui.fragments

import androidx.annotation.StringRes
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import us.huseli.soundboard2.R
import us.huseli.soundboard2.viewmodels.CategoryAddViewModel

@AndroidEntryPoint
class CategoryAddFragment : BaseCategoryEditFragment() {
    override val viewModel by activityViewModels<CategoryAddViewModel>()
    @StringRes
    override val dialogTitle = R.string.add_category
}