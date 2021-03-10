package me.ranko.autodark.core

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.IPackageManager
import android.content.pm.PackageManager
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import me.ranko.autodark.BuildConfig
import me.ranko.autodark.R
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.ShizukuProvider
import rikka.shizuku.SystemServiceHelper
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
            Timber.d(e,"Failed to pingBinder")
            return ShizukuStatus.DEAD
        } catch (e: Exception) {
            Timber.i(e, "WTF")
            return ShizukuStatus.DEAD
        }
    }

    fun startManagerActivity(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(MANAGER_APPLICATION_ID) ?: return false
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        ContextCompat.startActivity(context, intent, null)
        return true
    }

    fun buildShizukuDeadDialog(activity: Activity): AlertDialog {
        return AlertDialog.Builder(activity, R.style.SimpleDialogStyle)
            .setMessage(R.string.shizuku_connect_failed)
            .setNeutralButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.shizuku_open_manager) { _, _ -> startManagerActivity(activity) }
            .create()
    }

    fun grantWithShizuku() {
        mManager.grantRuntimePermission(BuildConfig.APPLICATION_ID, Manifest.permission.WRITE_SECURE_SETTINGS, android.os.Process.ROOT_UID)
    }
}