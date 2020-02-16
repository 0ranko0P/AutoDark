package me.ranko.autodark.Utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Paint
import android.view.Window
import android.view.WindowManager
import android.widget.TextView

/**
 * Simple view util
 *
 * @author  0ranko0P
 * */
object ViewUtil {
    fun isLandscape(context: Context) =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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

    /**
     * Applies a strike-through font style on a TextView
     *
     * @param   textView The target textView
     * @param   enabled Apply strike style or not
     *
     * @see     TextView.setPaintFlags
     * @see     Paint.STRIKE_THRU_TEXT_FLAG
     * */
    fun setStrikeFontStyle(textView: TextView, enabled: Boolean) {
        textView.paintFlags = if (enabled) {
            textView.paintFlags.or(Paint.STRIKE_THRU_TEXT_FLAG)
        } else {
            textView.paintFlags.and(Paint.STRIKE_THRU_TEXT_FLAG.inv())
        }
    }
}