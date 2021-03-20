package me.ranko.autodark.ui

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import me.ranko.autodark.Utils.ViewUtil

abstract class BaseListActivity : AppCompatActivity(), OnApplyWindowInsetsListener {

    protected var bottomNavHeight = 0
    protected var statusBarHeight = 0

    protected val isLandScape by lazy(LazyThreadSafetyMode.NONE) {
        ViewUtil.isLandscape(this) // avoid null resource
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.R && isLandScape.not()) {
            window.statusBarColor = Color.TRANSPARENT
            ViewUtil.setImmersiveNavBar(window)
        }
        ViewCompat.setOnApplyWindowInsetsListener(window!!.decorView.rootView, this)
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat? {
        val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        bottomNavHeight = systemBarInsets.bottom
        statusBarHeight = systemBarInsets.top

        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.R && isLandScape.not()) {
            applyInsetsToListPadding(statusBarHeight, bottomNavHeight)
        }
        v.setOnApplyWindowInsetsListener(null)
        return WindowInsetsCompat.CONSUMED
    }

    open fun applyInsetsToListPadding(top: Int, bottom: Int) {
        getListView()?.apply {
            setPadding(paddingLeft, paddingTop + top, paddingRight, paddingBottom + bottom)
        }
        getAppbar()?.apply {
            setPadding(paddingLeft, top, paddingRight, paddingBottom)
        }
    }

    abstract fun getListView(): View?

    abstract fun getAppbar(): View?
}