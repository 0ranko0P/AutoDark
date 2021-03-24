package me.ranko.autodark.Receivers

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemProperties
import android.util.Log
import de.robv.android.xposed.XposedBridge
import me.ranko.autodark.BuildConfig
import me.ranko.autodark.Constant
import me.ranko.autodark.xposed.ATMHooker
import me.ranko.autodark.xposed.IMEHooker
import java.lang.ref.WeakReference

/**
 * Broadcast receiver registered in SystemServer that updates ime status to [ATMHooker]
 * */
class InputMethodReceiver(context: Context, private val hooker: ATMHooker): BroadcastReceiver() {

    companion object {
        private const val TAG = IMEHooker.TAG

        private const val ACTION_INPUT_METHOD = "me.ranko0p.intent.action.IME"

        private const val EXTRA_KEY_INPUT_METHOD = "k_ime"

        var INSTANCE: InputMethodReceiver? = null

        fun register(context: Context, hooker: ATMHooker) {
            val receiver = InputMethodReceiver(context, hooker)
            val filter = IntentFilter(ACTION_INPUT_METHOD)
            filter.addAction(Intent.ACTION_SHUTDOWN)
            context.registerReceiver(receiver, filter)
            INSTANCE = receiver
        }

        fun sendImeUpdateBroadCast(context: Context, imePkg: String) {
            val intent = Intent(ACTION_INPUT_METHOD)
            intent.putExtra(EXTRA_KEY_INPUT_METHOD, imePkg)
            intent.setPackage(Constant.ANDROID_PACKAGE)
            context.sendBroadcast(intent, Constant.PERMISSION_RECEIVE_DARK_BROADCAST)
        }

        fun shouldHookIME(): Boolean {
            return SystemProperties.getBoolean(Constant.SYSTEM_PROP_HOOK_INPUT_METHOD, false)
        }

        fun enableHookIME(enabled: Boolean) {
            SystemProperties.set(Constant.SYSTEM_PROP_HOOK_INPUT_METHOD, enabled.toString())
        }
    }

    private val mContext = WeakReference(context)

    init {
        XposedBridge.log("onInit, IMEHooker online")
    }

    @SuppressLint("LogNotTimber")
    override fun onReceive(context: Context, intent: Intent) {
        if (BuildConfig.DEBUG) Log.d(TAG, "onReceive: action: ${intent.action}")

        when (intent.action) {
            ACTION_INPUT_METHOD -> {
                val pkg = intent.getStringExtra(EXTRA_KEY_INPUT_METHOD)
                if (BuildConfig.DEBUG) Log.d(TAG, "onImeUpdate: package: $pkg")

                hooker.updateForceDark(pkg!!)
            }

            Intent.ACTION_SHUTDOWN -> {
                destroy()
            }

            else -> Log.e(TAG, "Unknown action")
        }
    }

    fun destroy() {
        mContext.get()?.unregisterReceiver(this)
        mContext.clear()
        INSTANCE = null
    }
}