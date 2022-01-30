package me.ranko.autodark.ui

import android.app.Application
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import me.ranko.autodark.core.ShizukuApi
import me.ranko.autodark.core.ShizukuApi.REQUEST_CODE_SHIZUKU_PERMISSION
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

    private val permissionPre11Callback by lazy { ActivityResultCallback<Boolean> { result ->
        if (result) {
            onRequestPermissionResult(REQUEST_CODE_SHIZUKU_PERMISSION, PERMISSION_GRANTED)
        } else {
            onRequestPermissionResult(REQUEST_CODE_SHIZUKU_PERMISSION, PERMISSION_DENIED)
        }
    }}

    private lateinit var permissionPre11Launcher: ActivityResultLauncher<String>

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
        if (requestCode != REQUEST_CODE_SHIZUKU_PERMISSION) return
        if (grantResult == PERMISSION_GRANTED) {
            _status.value = ShizukuStatus.AVAILABLE
        } else {
            _status.value = ShizukuStatus.UNAUTHORIZED
        }
        _shizukuRequesting.value = false
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

    fun registerPermissionPre11(fragment: Fragment) {
        if (ShizukuApi.isPre11()) {
            permissionPre11Launcher =
                fragment.registerForActivityResult(RequestPermission(), permissionPre11Callback)
        }
    }

    fun registerPermissionPre11(activity: ComponentActivity) {
        if (ShizukuApi.isPre11()) {
            permissionPre11Launcher =
                activity.registerForActivityResult(RequestPermission(), permissionPre11Callback)
        }
    }

    fun requestPermission() {
        _shizukuRequesting.value = true
        if (ShizukuApi.isPre11()) {
            permissionPre11Launcher.launch(ShizukuProvider.PERMISSION)
        } else {
            Shizuku.requestPermission(REQUEST_CODE_SHIZUKU_PERMISSION)
        }
    }

    fun isShizukuAvailable(): Boolean = status.value == ShizukuStatus.AVAILABLE
}