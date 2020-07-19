package me.ranko.autodark.xposed

import android.app.Activity
import android.app.AndroidAppHelper
import android.app.UiModeManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.os.SystemProperties
import android.util.Log
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import me.ranko.autodark.AutoDarkApplication
import me.ranko.autodark.BuildConfig
import me.ranko.autodark.Constant
import me.ranko.autodark.Receivers.ActivityUpdateReceiver
import me.ranko.autodark.Utils.FileUtil
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Xposed inject class
 *
 * @author  0Ranko0P
 * */
class XCore : IXposedHookLoadPackage, IXposedHookZygoteInit {
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
                    val plantB: Boolean
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
                        prepareHook()
                    }
                }
            })
        }

        /**
         * Store block list flags to debug system properties if needed
         *
         * */
        private fun prepareHook() {
            try {
                if (Files.exists(Constant.BLOCK_LIST_SYSTEM_APP_CONFIG_PATH)) {
                    SystemProperties.set(Constant.SYSTEM_PROP_HOOK_SYSTEM_APPS, true.toString())
                }

                if (Files.exists(Constant.BLOCK_LIST_INPUT_METHOD_CONFIG_PATH)) {
                    SystemProperties.set(Constant.SYSTEM_PROP_HOOK_INPUT_METHOD, true.toString())
                }
            } catch (e: Exception) {
                XposedBridge.log(e)
            } finally {
                XposedBridge.log("onPrepareHook: hook System: ${SystemProperties.get(Constant.SYSTEM_PROP_HOOK_SYSTEM_APPS)}")
                XposedBridge.log("onPrepareHook: hook IME: ${SystemProperties.get(Constant.SYSTEM_PROP_HOOK_INPUT_METHOD)}")
            }
        }

        fun tryHookIME(lpparam: XC_LoadPackage.LoadPackageParam) {
            try {
                XposedHelpers.findAndHookMethod(InputMethodService::class.java, "onCreate", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        ActivityUpdateReceiver.sendNewActivity(
                            param.thisObject as Context,
                            lpparam.appInfo.packageName
                        )
                    }
                })
            } catch (ignore: Exception) {
            }
        }

        fun excludeSysApp(app: ApplicationInfo): Boolean {
            return (ApplicationInfo.FLAG_SYSTEM.and(app.flags) == ApplicationInfo.FLAG_SYSTEM) &&
                    SystemProperties.getBoolean(Constant.SYSTEM_PROP_HOOK_SYSTEM_APPS, false).not()
        }
    }

    /**
     * Hook activities before onCreate and send package to [ActivityUpdateReceiver]
     * */
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == Constant.ANDROID_PACKAGE && lpparam.processName == Constant.ANDROID_PACKAGE) {
            val sysServerClass = XposedHelpers.findClass("com.android.server.SystemServer", lpparam.classLoader)
            hookSystemService(sysServerClass)
        } else {
            if (excludeSysApp(lpparam.appInfo)) return

            if (lpparam.packageName == BuildConfig.APPLICATION_ID) {
                hookDarkApp(XposedHelpers.findClass(AutoDarkApplication::class.java.name, lpparam.classLoader))
                return
            }

            if (SystemProperties.get(Constant.SYSTEM_PROP_HOOK_INPUT_METHOD) == true.toString()) {
                tryHookIME (lpparam)
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

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        if (!startupParam.startsSystemServer) return

        // chmod 770 && chgrop system
        val darkDataDir = Paths.get(Constant.APP_DATA_DIR)
        FileUtil.chgrop(darkDataDir, "system")
        FileUtil.chmod(darkDataDir, FileUtil.PERMISSION_770)
        XposedBridge.log("initZygote: Block list permission modified")
    }
}