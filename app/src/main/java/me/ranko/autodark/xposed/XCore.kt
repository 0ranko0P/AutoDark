package me.ranko.autodark.xposed

import android.annotation.SuppressLint
import android.app.AndroidAppHelper
import android.content.Context
import android.os.Build
import android.os.SystemProperties
import android.util.Log
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import me.ranko.autodark.Constant
import me.ranko.autodark.Receivers.BlockListReceiver
import me.ranko.autodark.Receivers.InputMethodReceiver
import me.ranko.autodark.Receivers.InputMethodReceiver.Companion.shouldHookIME
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
}