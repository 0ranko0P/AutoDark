package me.ranko.autodark.xposed

import android.annotation.SuppressLint
import android.app.AndroidAppHelper
import android.content.Context
import android.content.pm.IPackageManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemProperties
import android.util.Log
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import me.ranko.autodark.BuildConfig
import me.ranko.autodark.Constant
import me.ranko.autodark.receivers.BlockListReceiver
import me.ranko.autodark.receivers.InputMethodReceiver
import me.ranko.autodark.receivers.InputMethodReceiver.Companion.shouldHookIME
import java.nio.file.Files

/**
 * Xposed inject class
 *
 * @author  0Ranko0P
 * */
@SuppressLint("LogNotTimber")
class XCore : IXposedHookLoadPackage, IXposedHookZygoteInit {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == Constant.ANDROID_PACKAGE) {
            // Hook ActivityTaskManagerService
            val hooker = ATMHooker.handleLoadPackage(lpparam)

            // Hook SystemServer#startCoreServices
            val sysClass =
                XposedHelpers.findClass("com.android.server.SystemServer", lpparam.classLoader)
            val param = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                emptyArray<Any>()
            } else {
                arrayOf("com.android.server.utils.TimingsTraceAndSlog")
            }

            // register a BroadCastReceiver to communicate with AutoDark
            XposedHelpers.findAndHookMethod(sysClass, "startCoreServices", *param, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val context: Context? = try {
                        val contextField = XposedHelpers.findField(sysClass, "mSystemContext")
                        contextField.get(param.thisObject) as Context
                    } catch (e: Exception) {
                        XposedBridge.log("PlanA failed on SDK ${Build.VERSION.SDK_INT} ${Log.getStackTraceString(e)}")
                        AndroidAppHelper.currentApplication()
                    }

                    if (context == null) {
                        throw NullPointerException("PlanB failed: Context is null")
                    } else {
                        BlockListReceiver.register(context, hooker)
                        if (shouldHookIME()) {
                            InputMethodReceiver.register(context, hooker)
                        }
                        grantPermission(sysClass, param.thisObject)
                    }
                }
            })
        } else {
            if (shouldHookIME()) IMEHooker.handleLoadPackage(lpparam)
        }
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        if (startupParam.startsSystemServer) {
            // Store block list flags to debug system properties if needed
            if (Files.exists(Constant.BLOCK_LIST_INPUT_METHOD_CONFIG_PATH)) {
                SystemProperties.set(Constant.SYSTEM_PROP_HOOK_INPUT_METHOD, true.toString())
            }
            XposedBridge.log("initZygote: Hook IME: ${shouldHookIME()}")
        }
    }

    companion object {
        private const val PERMISSION_INTERACT_ACROSS_USERS = "android.permission.INTERACT_ACROSS_USERS"

        private fun grantPermission(sysClass: Class<*>, sysServer: Any) {
            try {
                val pkgField = XposedHelpers.findField(sysClass, "mPackageManagerService")
                val iPkgManager = pkgField.get(sysServer) as IPackageManager
                val usersPermission = iPkgManager.checkPermission(
                    PERMISSION_INTERACT_ACROSS_USERS,
                    BuildConfig.APPLICATION_ID,
                    android.os.Process.ROOT_UID
                )
                if (usersPermission != PackageManager.PERMISSION_GRANTED) {
                    iPkgManager.grantRuntimePermission(
                        BuildConfig.APPLICATION_ID,
                        PERMISSION_INTERACT_ACROSS_USERS,
                        android.os.Process.ROOT_UID
                    )
                }
            } catch (e: Exception) {
                XposedBridge.log(e)
            }
        }
    }
}