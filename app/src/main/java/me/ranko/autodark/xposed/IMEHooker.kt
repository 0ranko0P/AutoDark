package me.ranko.autodark.xposed

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.util.Log
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import me.ranko.autodark.BuildConfig
import me.ranko.autodark.receivers.InputMethodReceiver
import java.lang.reflect.Field

@SuppressLint("LogNotTimber")
class IMEHooker(private val thisPackage: String) : XC_MethodHook() {

    companion object {
        const val TAG = "IMEHooker"

        fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
            val hooker = IMEHooker(lpparam.packageName)
            XposedHelpers.findAndHookMethod(InputMethodService::class.java, "updateInputViewShown", hooker)
            XposedHelpers.findAndHookMethod(InputMethodService::class.java, "setInputView", View::class.java, hooker.inputViewHooker)
        }
    }

    private var invalidated = false

    private var mUiManager: UiModeManager? = null

    /**
     * Hook [InputMethodService.setInputView] and tag invalidated view
     *
     * @see invalidated
     * @see beforeHookedMethod
     * */
    private val inputViewHooker = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            val mView: View = param.args[0] as View
            if (mView.getTag(mView.id) == null && invalidated) {
                mView.setTag(mView.id, ATMHooker.isForceDark())
                invalidated = false
            }
        }
    }

    override fun beforeHookedMethod(param: MethodHookParam) {
        val mViewField = XposedHelpers.findField(InputMethodService::class.java, "mInputView")
        val ime = param.thisObject as InputMethodService
        if (notNightMode(ime)) return

        try {
            // null cast on first view update
            val mInputView: View = mViewField.get(ime) as View
            val tag = mInputView.getTag(mInputView.id) ?: throw NullPointerException()

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "onUpdateInputViewShown: tag: $tag, forceDark: ${ATMHooker.isForceDark()}")
            }
        } catch (ignored: NullPointerException) {
            invalidateView(mViewField, ime)
        } catch (e: Exception) {
            Log.w(TAG,"onUpdateInputViewShow: ", e)
        }
    }

    private fun notNightMode(service: InputMethodService): Boolean {
        if (mUiManager == null) {
            mUiManager = service.applicationContext.getSystemService(UiModeManager::class.java)!!
        }
        return mUiManager?.nightMode == UiModeManager.MODE_NIGHT_NO
    }

    private fun invalidateView(mInputViewField: Field, service: InputMethodService) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onUpdateInputViewShown: invalidating mInputView")
        }
        InputMethodReceiver.sendImeUpdateBroadCast(service, thisPackage)
        SystemClock.sleep(200L) // delay for binder
        mInputViewField.set(service, null)
        invalidated = true
    }
}