package me.ranko.autodark.xposed

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.annotation.VisibleForTesting
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage
import me.ranko.autodark.BuildConfig
import me.ranko.autodark.Constant

@SuppressLint("LogNotTimber")
class ATMHooker private constructor(lpparam: XC_LoadPackage.LoadPackageParam) : XC_MethodHook() {

    companion object {
        private const val TAG = "ATMHooker"

        private const val ATM_CLASS = "com.android.server.wm.ActivityTaskManagerService"
        private const val ASC_CLASS = "com.android.server.wm.ActivityStartController"

        private const val CLASS_TASK_V29 = "com.android.server.wm.TaskRecord"
        private const val CLASS_TASK_V30 = "com.android.server.wm.Task"

        private var TASK_CLASS: Class<*>? = null
        private var ASC_PARAM_TASK_INDEX: Int = -1

        private var ASC_PARAM_INTENT_INDEX = -1
        private var ATM_PARAM_INTENT_INDEX = -1

        fun isForceDark(): Boolean = SystemProperties.getBoolean(Constant.SYSTEM_PROP_FORCE_DARK, false)

        fun getRealActivity(task: Any): ComponentName {
            return XposedHelpers.getObjectField(task, "realActivity") as ComponentName
        }

        /**
         * https://android.googlesource.com/platform/frameworks/base/+/android-11.0.0_r1/services/core/java/com/android/server/wm/ActivityTaskManagerService.java#1077
         * */
        @VisibleForTesting
        fun buildAtmParamList(): Array<Any> {
            val paramListV30 = arrayListOf(
                "android.app.IApplicationThread", //caller
                String::class.java, // callingPackage
                String::class.java, // callingFeatureId
                Intent::class.java, // intent
                String::class.java, // resolvedType
                IBinder::class.java, // resultTo
                String::class.java, // resultWho
                Int::class.javaPrimitiveType, // requestCode
                Int::class.javaPrimitiveType, // startFlags
                "android.app.ProfilerInfo", // profilerInfo
                Bundle::class.java, // bOptions
                Int::class.javaPrimitiveType, // userId
                Boolean::class.javaPrimitiveType // validateIncomingUser
            )

            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                paramListV30.removeAt(2) // callingFeatureId
            }
            return paramListV30.toArray()
        }

        /**
         * https://android.googlesource.com/platform/frameworks/base/+/android-11.0.0_r1/services/core/java/com/android/server/wm/ActivityStartController.java#283
         * */
        @VisibleForTesting
        fun buildAscParamList(): Array<Any> {
            val paramListV30 = arrayListOf(
                Int::class.javaPrimitiveType, // uid
                Int::class.javaPrimitiveType, // realCallingPid
                Int::class.javaPrimitiveType, // realCallingUid
                String::class.java, // callingPackage,
                String::class.java, // callingFeatureId
                Intent::class.java, // intent
                String::class.java, // resolvedType
                IBinder::class.java, //resultTo
                String::class.java, // resultWho
                Int::class.javaPrimitiveType, // requestCode
                Int::class.javaPrimitiveType, // startFlags
                "com.android.server.wm.SafeActivityOptions", // options
                Int::class.javaPrimitiveType, // userId
                String::class.java, // reason,
                Boolean::class.javaPrimitiveType, // validateIncomingUser
                "com.android.server.am.PendingIntentRecord", // originatingPendingIntent,
                Boolean::class.javaPrimitiveType //allowBackgroundActivityStart
            )

            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                paramListV30.removeAt(4) // callingFeatureId
            }
            paramListV30.add(ASC_PARAM_TASK_INDEX, TASK_CLASS) // inTask
            return paramListV30.toArray()
        }

        fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam): ATMHooker {
            val amsClass = XposedHelpers.findClass(ATM_CLASS, lpparam.classLoader)
            val ascClass = XposedHelpers.findClass(ASC_CLASS, lpparam.classLoader)
            val hooker = ATMHooker(lpparam)
            findAndHookMethod(amsClass, "startActivityAsUser", *buildAtmParamList(), hooker)
            findAndHookMethod(ascClass, "startActivityInPackage", *buildAscParamList(), hooker.ascHooker)
            return hooker
        }
    }

    /**
     * Hook **ActivityStartController#startActivityInPackage** and report
     * [Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY] activity to [ATMHooker]
     * */
    private val ascHooker = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            val intent = param.args[ASC_PARAM_INTENT_INDEX] as Intent
            if (intent.flags.and(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) {
                val task = param.args[ASC_PARAM_TASK_INDEX]
                val realActivity = getRealActivity(task)
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "onStartActivityInPackage: realAct: $realActivity")
                }
                updateForceDark(realActivity.packageName)
            }
        }
    }

    private lateinit var mUiManager: UiModeManager


    /**
     * Holds blocked apps
     * */
    private var mBlockSet: Set<String> = emptySet()

    /**
     * **True** after AutoDark initialized block list
     *
     * @see me.ranko.autodark.Receivers.DarkModeAlarmReceiver
     * */
    private var ininitalized = false

    init {
         when(Build.VERSION.SDK_INT) {
            Build.VERSION_CODES.Q -> {
                ASC_PARAM_INTENT_INDEX = 4
                ATM_PARAM_INTENT_INDEX = 2
                TASK_CLASS = XposedHelpers.findClass(CLASS_TASK_V29, lpparam.classLoader)
                ASC_PARAM_TASK_INDEX = 12
            }

            else -> {
                ASC_PARAM_INTENT_INDEX = 5
                ATM_PARAM_INTENT_INDEX = 3
                TASK_CLASS = XposedHelpers.findClass(CLASS_TASK_V30, lpparam.classLoader)
                ASC_PARAM_TASK_INDEX = 13
            }
        }

        XposedBridge.log("onInit, ATMHooker is online, Uid: ${Process.myUid()}")
    }

    override fun afterHookedMethod(param: MethodHookParam) {
        if (ininitalized) {
            if (mUiManager.nightMode == UiModeManager.MODE_NIGHT_NO) return

            val intent = param.args[ATM_PARAM_INTENT_INDEX] as Intent
            val pkg = intent.component?.packageName ?: return
            updateForceDark(pkg)
        }
    }

    fun updateForceDark(pkg: String) {
        if (ininitalized) {
            if (mUiManager.nightMode == UiModeManager.MODE_NIGHT_NO) return

            val force: Boolean = isForceDark()
            val blocked = mBlockSet.contains(pkg)

            // ForceDark ON  == blocked --> ForceDark OFF
            // ForceDark OFF == blocked --> ForceDark ON
            if (blocked == force) {
                SystemProperties.set(Constant.SYSTEM_PROP_FORCE_DARK, force.not().toString())
                Log.v(TAG, "onUpdatePkg: Target: $pkg switching ForceDark from $force to ${isForceDark()}")
            }
        }
    }

    fun updateList(blockList: Collection<String>, context: Context) {
        if (ininitalized.not()) {
            mUiManager = context.getSystemService(UiModeManager::class.java)!!
            XposedBridge.log("onInitBlockList: size: ${blockList.size}")
        }
        ininitalized = true
        mBlockSet = if (blockList.isEmpty()) {
            emptySet()
        } else {
            HashSet(blockList)
        }
    }

    fun printBlockList() {
        Log.e(TAG, "onPrintSet: size: ${mBlockSet.size}")
        val list = mBlockSet.sorted()
        for (i in list.indices) {
            Log.e(TAG, "$i : ${list[i]}")
        }
    }
}