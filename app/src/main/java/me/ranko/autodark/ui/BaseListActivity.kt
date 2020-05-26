package me.ranko.autodark.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.AppBarLayout
import kotlinx.android.synthetic.main.activity_block_list.*
import me.ranko.autodark.Utils.ViewUtil

abstract class BaseListActivity : AppCompatActivity(), OnApplyWindowInsetsListener {

    companion object{
        private var bottomNavHeight = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.statusBarColor = Color.TRANSPARENT
        ViewUtil.setImmersiveNavBar(window)
        super.onCreate(savedInstanceState)
        // get navBar height then set it as bottom padding to RecyclerView
        ViewCompat.setOnApplyWindowInsetsListener(window!!.decorView.rootView, this)
    }

    override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat? {
        bottomNavHeight = insets.systemWindowInsetBottom
        onNavBarHeightAvailable(bottomNavHeight)
        v.setOnApplyWindowInsetsListener(null)
        return insets.consumeSystemWindowInsets()
    }

    abstract fun onNavBarHeightAvailable(height: Int)

    fun getNavBarHeight(): Int = bottomNavHeight
}