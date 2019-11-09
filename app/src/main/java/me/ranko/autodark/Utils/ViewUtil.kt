package me.ranko.autodark.Utils

import android.content.res.Configuration
import android.content.res.Resources
import android.view.Window
import android.view.WindowManager

/**
 * Simple view util
 *
 * @author  0ranko0P
 * */
object ViewUtil {
    fun isLandscape(window: Window) =
        window.context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun setImmersiveNavBar(window: Window) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
    }

    /**
     * @return  System status bar height
     *
     * @link    https://stackoverflow.com/questions/3407256/height-of-status-bar-in-android/47125610#47125610
     * */
    fun getStatusBarHeight(res: Resources): Int {
        val resourceId = res.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            return res.getDimensionPixelSize(resourceId)
        }
        return 1
    }
}