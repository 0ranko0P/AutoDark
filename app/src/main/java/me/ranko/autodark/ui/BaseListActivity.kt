package me.ranko.autodark.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import me.ranko.autodark.Utils.ViewUtil

abstract class BaseListActivity : AppCompatActivity(), OnApplyWindowInsetsListener {

    protected var bottomNavHeight = 0
    protected var statusBarHeight = 0

    protected val isLandScape by lazy(LazyThreadSafetyMode.NONE) {
        ViewUtil.isLandscape(this) // avoid null resource
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isLandScape.not()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
            } else {
                getRootView().systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE.or(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(window!!.decorView.rootView, this)
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat? {
        v.setOnApplyWindowInsetsListener(null)
        val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        bottomNavHeight = systemBarInsets.bottom
        statusBarHeight = systemBarInsets.top
        if (isLandScape.not()) {
            applyInsetsToListPadding(statusBarHeight, bottomNavHeight)
        }
        return WindowInsetsCompat.CONSUMED
    }

    open fun applyInsetsToListPadding(top: Int, bottom: Int) {
        getAppbar()?.updatePadding(top = top)
        getListView()?.apply {
            updatePadding(top = paddingTop + top, bottom = paddingBottom + bottom)
        }
    }

    abstract fun getAppbar(): View?

    abstract fun getRootView(): View

    abstract fun getListView(): View?
}