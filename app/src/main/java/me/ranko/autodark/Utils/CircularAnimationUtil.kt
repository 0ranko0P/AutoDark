package me.ranko.autodark.Utils

import android.animation.Animator
import android.view.View
import android.view.ViewAnimationUtils
import androidx.annotation.Size
import kotlin.math.max

/**
 * Util to create circular animation
 *
 * @link    https://developer.android.com/training/animation/reveal-or-hide-view.html#Reveal
 * */
object CircularAnimationUtil {

    /**
     * Build circular Animator with a view
     *
     * @param   startView circle animate starts from this view's center
     * @param   target    circle animate perform on this view, will automatic set visibility
     */
    @JvmStatic
    fun buildAnimator(startView: View, target: View, targetRadius:Float = 0f): Animator {
        return buildAnimator(getViewCenterCoordinate(startView), target, targetRadius)
    }

    /**
     * Build circular animator with coordinates
     *
     * @param   coordinates   The X,Y coordinate that circle animate starts
     * @param   target    The view that Animator will perform on
     *
     * @return  In/Out circular animator depends on target view's visibility
     *
     * @see     ViewAnimationUtils.createCircularReveal
     */
    @JvmStatic
    fun buildAnimator(@Size(2) coordinates: IntArray, target: View, targetRadius:Float = 0f): Animator {
        // is in/out animation
        val aType = target.visibility == View.VISIBLE

        val radius = max(target.width, target.height).toFloat()
        val start = if (aType) radius else targetRadius
        val end = if (aType) targetRadius else radius

        return ViewAnimationUtils.createCircularReveal(
            target,
            coordinates[0],
            coordinates[1],
            start,
            end
        )
    }

    @JvmStatic
    fun getViewCenterCoordinate(view: View): IntArray {
        val cx = view.x + view.width / 2
        val cy = view.y + view.height / 2
        return intArrayOf(cx.toInt(), cy.toInt())
    }
}