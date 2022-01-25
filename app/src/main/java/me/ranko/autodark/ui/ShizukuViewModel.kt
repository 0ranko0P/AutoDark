package me.ranko.autodark.ui

import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import me.ranko.autodark.core.ShizukuApi
import me.ranko.autodark.core.ShizukuStatus
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import timber.log.Timber

/**
 * ViewModel that provides observable Shizuku status
 *
 * @see ShizukuStatus
 * @see Shizuku.addBinderDeadListener
 * */
open class ShizukuViewModel(application: Application) : AndroidViewModel(application),
    DefaultLifecycleObserver, Shizuku.OnRequestPermissionResultListener {

    private val _status = MutableLiveData(ShizukuApi.checkShizukuCompat(application))
    val status: LiveData<ShizukuStatus>
        get() = _status

    private val _shizukuRequesting: MutableLiveData<Boolean?> = MutableLiveData(null)

    /**
     * Permission request result, **Null** if there's no pending request.
     * */
    val shizukuRequesting: LiveData<Boolean?>
        get() = _shizukuRequesting

    private var mPermissionListener: Shizuku.OnRequestPermissionResultListener? = null

    private val mBinderReceiver = Shizuku.OnBinderReceivedListener {
        _status.value = ShizukuApi.checkShizukuCompat(application, true)
        if (_status.value == ShizukuStatus.UNAUTHORIZED && mPermissionListener == null) {
            mPermissionListener = this
            Shizuku.addRequestPermissionResultListener(mPermissionListener!!)
        }
    }

    private val mBinderDeadReceiver = Shizuku.OnBinderDeadListener {
        Timber.i("Shizuku Binder dead ${hashCode()}.")
        _status.value = ShizukuStatus.DEAD
    }

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        if (requestCode != ShizukuApi.REQUEST_CODE_SHIZUKU_PERMISSION) return
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            _status.value = ShizukuStatus.AVAILABLE
        } else {
            _status.value = ShizukuStatus.UNAUTHORIZED
        }
        _shizukuRequesting.value = false
    }

    fun onRequestPermissionResultPre11(requestCode: Int, grantResult: Int) {
        mPermissionListener!!.onRequestPermissionResult(requestCode, grantResult)
    }

    override fun onCreate(owner: LifecycleOwner) {
        Shizuku.addBinderReceivedListenerSticky(mBinderReceiver)
        Shizuku.addBinderDeadListener(mBinderDeadReceiver)
    }

    override fun onStop(owner: LifecycleOwner) {
        if (_shizukuRequesting.value == true) _shizukuRequesting.value = null
    }

    override fun onDestroy(owner: LifecycleOwner) {
        Shizuku.removeBinderReceivedListener(mBinderReceiver)
        Shizuku.removeBinderDeadListener(mBinderDeadReceiver)
        mPermissionListener?.let { Shizuku.removeRequestPermissionResultListener(it) }
    }

    fun requestPermission(frag: Fragment) {
        _shizukuRequesting.value = true
        if (ShizukuApi.isPre11()) {
            frag.requestPermissions(
                arrayOf(ShizukuProvider.PERMISSION),
                ShizukuApi.REQUEST_CODE_SHIZUKU_PERMISSION
            )
        } else {
            Shizuku.requestPermission(ShizukuApi.REQUEST_CODE_SHIZUKU_PERMISSION)
        }
    }

    fun requestPermission(activity: Activity) {
        _shizukuRequesting.value = true
        if (ShizukuApi.isPre11()) {
            activity.requestPermissions(
                arrayOf(ShizukuProvider.PERMISSION),
                ShizukuApi.REQUEST_CODE_SHIZUKU_PERMISSION
            )
        } else {
            Shizuku.requestPermission(ShizukuApi.REQUEST_CODE_SHIZUKU_PERMISSION)
        }
    }

    fun isShizukuAvailable(): Boolean = status.value == ShizukuStatus.AVAILABLE
}