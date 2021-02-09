package me.ranko.autodark.core

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.IPackageManager
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import me.ranko.autodark.BuildConfig
import rikka.shizuku.*
import timber.log.Timber
import java.lang.Exception

enum class ShizukuStatus {
    NOT_INSTALL, DEAD, UNAUTHORIZED,
    /**
     * Indicates Shizuku is running and authorized
     * operate on this status only
     * */
    AVAILABLE
}

object ShizukuApi {
    private const val MANAGER_APPLICATION_ID = "moe.shizuku.privileged.api"

    private val mManager:IPackageManager by lazy {
        IPackageManager.Stub.asInterface(ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package")))
    }

    fun checkShizuku(context: Context): ShizukuStatus {
        if (!ShizukuProvider.isShizukuInstalled(context)) return ShizukuStatus.NOT_INSTALL

        try {
            val permission = if (!Shizuku.isPreV11() && Shizuku.getVersion() >= 11) {
                Shizuku.checkSelfPermission()
            } else {
                ContextCompat.checkSelfPermission(context, ShizukuProvider.PERMISSION)
            }
            return if (permission == PackageManager.PERMISSION_GRANTED) {
                ShizukuStatus.AVAILABLE
            } else {
                ShizukuStatus.UNAUTHORIZED
            }
        } catch (e: SecurityException) {
            // service version below v11 and the app have't get the permission
            return ShizukuStatus.UNAUTHORIZED
        } catch (e: IllegalStateException) {
            Timber.d("checkShizuku: Failed to pingBinder")
            return ShizukuStatus.DEAD
        } catch (e: Exception) {
            Timber.d(e, "checkShizuku: WTF")
            return ShizukuStatus.DEAD
        }
    }

    fun startManagerActivity(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(MANAGER_APPLICATION_ID) ?: return false
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        ContextCompat.startActivity(context, intent, null)
        return true
    }

    fun grantWithShizuku() {
        mManager.grantRuntimePermission(BuildConfig.APPLICATION_ID, Manifest.permission.WRITE_SECURE_SETTINGS, android.os.Process.ROOT_UID)
    }
}