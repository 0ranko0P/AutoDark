package me.ranko.autodark.Utils

import android.content.Context
import android.content.res.Configuration
import android.graphics.Paint
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.MenuItem
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import androidx.annotation.ColorInt

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

    fun setMenuItemTitleColor(item: MenuItem, @ColorInt color: Int, title: CharSequence = item.title) {
        val spannable = SpannableString(title)
        spannable.setSpan(ForegroundColorSpan(color), 0, title.length, 0)
        item.title = spannable
    }

    fun getAttrColor(context: Context, attr: Int): Int {
        val ta = context.obtainStyledAttributes(intArrayOf(attr))
        val colorAccent = ta.getColor(0, 0)
        ta.recycle()
        return colorAccent
    }
}