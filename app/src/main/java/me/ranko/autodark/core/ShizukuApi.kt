package me.ranko.autodark.core

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.IPackageManager
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.ranko.autodark.BuildConfig
import moe.shizuku.api.*
import timber.log.Timber

enum class ShizukuStatus {
    NOT_INSTALL, DEAD, UNAUTHORIZED,
    /**
     * Indicates Shizuku is running and authorized
     * operate on this status only
     * */
    AVAILABLE
}

object ShizukuApi {

    private val mManager:IPackageManager by lazy {
        IPackageManager.Stub.asInterface(ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package")))
    }

    suspend fun checkShizuku(context: Context): ShizukuStatus {
        if (!ShizukuProvider.isShizukuInstalled(context)) return ShizukuStatus.NOT_INSTALL

        if (!checkPermission(context)) return ShizukuStatus.UNAUTHORIZED

        return withContext(Dispatchers.IO) {
            if (ShizukuService.pingBinder()) {
                // Shizuku v3 binder received
                Timber.d("checkShizuku: binder received")
                ShizukuStatus.AVAILABLE
            } else {
                // Shizuku v3 may not running, notify user
                Timber.d("checkShizuku: Failed to pingBinder ")
                ShizukuStatus.DEAD
            }
        }
    }

    fun startManagerActivity(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(ShizukuApiConstants.MANAGER_APPLICATION_ID) ?: return false
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        ContextCompat.startActivity(context, intent, null)
        return true
    }

    fun grantWithShizuku() {
        mManager.grantRuntimePermission(BuildConfig.APPLICATION_ID, Manifest.permission.WRITE_SECURE_SETTINGS, android.os.Process.ROOT_UID)
    }

    fun checkPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, ShizukuApiConstants.PERMISSION) == PackageManager.PERMISSION_GRANTED
}