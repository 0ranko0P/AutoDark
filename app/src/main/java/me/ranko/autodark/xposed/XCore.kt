package me.ranko.autodark.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import me.ranko.autodark.AutoDarkApplication
import me.ranko.autodark.BuildConfig
import java.lang.Boolean

class XCore : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "XCore"

        @JvmStatic
        private fun hookDarkApp(darkAppClass: Class<*>) {
            XposedHelpers.findAndHookMethod(darkAppClass, "onCreate", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val filed = XposedHelpers.findFirstFieldByExactType(darkAppClass, Boolean.TYPE)
                    filed.isAccessible = true
                    filed.setBoolean(param.thisObject, true)
                }
            })
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == BuildConfig.APPLICATION_ID) {
            hookDarkApp(XposedHelpers.findClass(AutoDarkApplication::class.java.name, lpparam.classLoader))
        }
    }
}