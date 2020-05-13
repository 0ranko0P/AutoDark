package me.ranko.autodark.xposed

import android.app.Activity
import android.app.AndroidAppHelper
import android.app.UiModeManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import me.ranko.autodark.AutoDarkApplication
import me.ranko.autodark.BuildConfig
import me.ranko.autodark.Receivers.ActivityUpdateReceiver

/**
 * Xposed inject class
 *
 * @author  0Ranko0P
 * */
class XCore : IXposedHookLoadPackage {
    companion object {

        private const val TAG = "XCore"

        @JvmStatic
        private fun hookDarkApp(darkAppClass: Class<*>) {
            XposedHelpers.findAndHookMethod(darkAppClass, "onCreate", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val field = XposedHelpers.findFirstFieldByExactType(darkAppClass, java.lang.Boolean.TYPE)
                    field.isAccessible = true
                    field.setBoolean(param.thisObject, true)
                    field.isAccessible = false
                }
            })
        }

        /**
         * Hook SystemServer after startCoreServices() and
         * register a BroadCastReceiver to communicate with AutoDark,
         * it also receives new activities.
         *
         * @see     ActivityUpdateReceiver
         * */
        @JvmStatic
        private fun hookSystemService(sysClass:Class<*>) {
            XposedHelpers.findAndHookMethod(sysClass, "startCoreServices", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    var plantB = false
                    var context: Context? = null
                    try {
                        val contextField = XposedHelpers.findField(sysClass, "mSystemContext")
                        context = contextField.get(param.thisObject) as Context
                    } catch (e: Exception) {
                        XposedBridge.log("PlantA failed on SDK ${Build.VERSION.SDK_INT} ${Log.getStackTraceString(e)}")
                    } finally {
                        plantB = context == null
                    }

                    if (plantB) context = AndroidAppHelper.currentApplication()
                    if (context == null) {
                        throw NullPointerException("PlantB failed: Context is null")
                    } else {
                        ActivityUpdateReceiver.register(context)
                    }
                }
            })
        }

        fun isSystemApp(app: ApplicationInfo): Boolean = ApplicationInfo.FLAG_SYSTEM.and(app.flags) == ApplicationInfo.FLAG_SYSTEM
    }

    /**
     * Hook activities before onCreate and send package to [ActivityUpdateReceiver]
     * */
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == "android") {
            val sysServerClass = XposedHelpers.findClass("com.android.server.SystemServer", lpparam.classLoader)
            hookSystemService(sysServerClass)
        } else {
            // System apps usually supports dark mode
            if (isSystemApp(lpparam.appInfo)) return

            if (lpparam.packageName == BuildConfig.APPLICATION_ID) {
                hookDarkApp(XposedHelpers.findClass(AutoDarkApplication::class.java.name, lpparam.classLoader))
                return
            }

            XposedHelpers.findAndHookMethod(Activity::class.java, "onCreate", Bundle::class.java, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val start = System.currentTimeMillis()
                    val manager =
                        (param.thisObject as Activity).getSystemService(UiModeManager::class.java)
                    if (manager.nightMode == UiModeManager.MODE_NIGHT_NO) return

                    Log.v(TAG, "beforeCreate: findUIManager cost: ${System.currentTimeMillis() - start}ms")
                    Log.v(TAG, "beforeCreate: sendActivity: ${lpparam.appInfo.packageName}/${param.thisObject::class.java}")
                    ActivityUpdateReceiver.sendNewActivity(
                        param.thisObject as Activity,
                        lpparam.appInfo.packageName
                    )
                }
            })
        }
    }
}