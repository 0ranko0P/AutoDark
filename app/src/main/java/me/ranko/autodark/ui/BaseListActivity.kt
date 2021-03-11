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

    companion object{
        private var bottomNavHeight = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // don't apply insets on R
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) return

        window.statusBarColor = Color.TRANSPARENT
        if (!ViewUtil.isLandscape(this)) {
            ViewUtil.setImmersiveNavBar(window)
        }
        // get navBar height then set it as bottom padding to RecyclerView
        ViewCompat.setOnApplyWindowInsetsListener(window!!.decorView.rootView, this)
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat? {
        bottomNavHeight = insets.systemWindowInsetBottom
        applyInsetsToListPadding(insets.systemWindowInsetTop, bottomNavHeight)
        v.setOnApplyWindowInsetsListener(null)
        return insets.consumeSystemWindowInsets()
    }

    open fun applyInsetsToListPadding(top: Int, bottom: Int) {
        getListView()?.apply {
            setPadding(paddingLeft, paddingTop + top, paddingRight, paddingBottom + bottom)
        }
    }

    abstract fun getListView(): View?

    fun getNavBarHeight(): Int = bottomNavHeight
}