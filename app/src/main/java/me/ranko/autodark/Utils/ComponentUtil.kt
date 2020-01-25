package me.ranko.autodark.Utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object ComponentUtil {

    @JvmStatic
    fun isEnabled(context: Context, receiver: Class<*>): Boolean {
        val component = ComponentName(context, receiver)
        val status = context.packageManager.getComponentEnabledSetting(component)
        return status <= PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }
}