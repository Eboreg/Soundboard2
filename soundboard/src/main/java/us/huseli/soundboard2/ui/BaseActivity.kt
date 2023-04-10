package us.huseli.soundboard2.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit

abstract class BaseActivity<T : ViewDataBinding> : AppCompatActivity() {
    protected open lateinit var binding: T

    internal fun showFragment(fragmentClass: Class<out Fragment>, args: Bundle? = null) {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            add(fragmentClass, args, null)
        }
    }
}