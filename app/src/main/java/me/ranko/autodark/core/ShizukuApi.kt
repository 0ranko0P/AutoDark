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
import me.ranko.autodark.Exception.CommandExecuteError
import me.ranko.autodark.Utils.ShellJobUtil
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
        if(!ShizukuClientHelper.isManagerV3Installed(context)) return ShizukuStatus.NOT_INSTALL

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

    suspend fun runShizukuShell(vararg commands: String) {
        runShizukuShellForValue(*commands)
    }

    suspend fun runShizukuShellForValue(vararg commands: String): String? = withContext(Dispatchers.IO) {
        if(commands.isEmpty()) throw IllegalArgumentException("No commands to execute!")

        var process: Process? = null
        try {
            process = ShizukuService.newProcess(arrayOf("sh"), null, null)
            process.outputStream.use {
                commands.forEach {command ->
                    it.write("$command\n".toByteArray())
                }
                it.write("exit\n".toByteArray())
            }

            val result = ShellJobUtil.readStdout(process.inputStream, true)
            if (process.waitFor() != 0)
                throw CommandExecuteError(ShellJobUtil.readStdout(process.errorStream, false))

            return@withContext result
        } catch (e: Exception) {
            throw CommandExecuteError(e)
        } finally {
            process?.destroy()
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