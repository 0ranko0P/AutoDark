package me.ranko.autodark.core

import android.Manifest
import android.app.Activity
import android.app.IWallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.IPackageManager
import android.content.pm.PackageManager
import androidx.annotation.NonNull
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import me.ranko.autodark.AutoDarkApplication
import me.ranko.autodark.BuildConfig
import me.ranko.autodark.Constant
import me.ranko.autodark.R
import rikka.shizuku.*
import timber.log.Timber
import rikka.shizuku.ShizukuProvider

enum class ShizukuStatus {
    /* Shizuku only */
    NOT_INSTALL,

    /**
     * Shizuku only
     *
     * @see ShizukuApi.buildShizukuDeadDialog
     * */
    DEAD,

    /**
     * Binder received but unauthorized
     * */
    UNAUTHORIZED,

    /**
     * Indicates Shizuku or Sui is running and authorized.
     * Perform operation on this status only
     * */
    AVAILABLE
}

object ShizukuApi {
    private const val MANAGER_APPLICATION_ID = "moe.shizuku.privileged.api"

    const val SUI_COLOR = "light_green"

    const val SHIZUKU_COLOR = "indigo"

    const val REQUEST_CODE_SHIZUKU_PERMISSION = 7

    private val mWallpaperManager: IWallpaperManager by lazy {
        IWallpaperManager.Stub.asInterface(ShizukuBinderWrapper(SystemServiceHelper.getSystemService("wallpaper")))
    }

    private val mManager: IPackageManager by lazy {
        IPackageManager.Stub.asInterface(ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package")))
    }

    fun checkShizukuCompat(context: Context, skipMangerCheck: Boolean = false): ShizukuStatus {
        if (skipMangerCheck.not() && isShizukuInstalled(context).not()) {
            return ShizukuStatus.NOT_INSTALL
        }

        try {
            val permission = if (isPre11().not()) {
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
            return ShizukuStatus.DEAD
        } catch (e: Exception) {
            Timber.i(e, "WTF")
            return ShizukuStatus.DEAD
        }
    }

    private fun startManagerActivity(context: Context): Boolean {
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

    /**
     * Return if Shizuku or Sui is installed.
     *
     * @param context Context
     * @return true if Shizuku or Sui is installed
     */
    fun isShizukuInstalled(@NonNull context: Context): Boolean {
        if (AutoDarkApplication.isSui) return true
        return try {
            context.packageManager.getPermissionInfo(ShizukuProvider.PERMISSION, 0) != null
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getIWallpaperManager(): IWallpaperManager = mWallpaperManager

    fun setForceDark(enabled: Boolean): Boolean {
        try {
            ShizukuSystemProperties.set(Constant.SYSTEM_PROP_FORCE_DARK, enabled.toString())
            return true
        } catch (ignored: SecurityException) {
        } catch (e: Exception) {
            Timber.w(e)
        }
        return false
    }

    fun grantWithShizuku() {
        mManager.grantRuntimePermission(BuildConfig.APPLICATION_ID, Manifest.permission.WRITE_SECURE_SETTINGS, android.os.Process.ROOT_UID)
    }

    fun isPre11(): Boolean = try {
        Shizuku.isPreV11() && Shizuku.getVersion() <= 10
    } catch (e: SecurityException) {
        true
    }
}