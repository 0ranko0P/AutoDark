package me.ranko.autodark.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.ranko.autodark.AutoDarkApplication.isShizukuV3Failed
import me.ranko.autodark.Exception.CommandExecuteError
import me.ranko.autodark.Utils.ShellJobUtil
import moe.shizuku.api.ShizukuService

object ShizukuApi {

    suspend fun checkShizuku(): Boolean {
        // Shizuku v3 service will send binder via Content Provider to this process when this activity comes foreground.

        // Wait a few seconds here for binder

        delay(1000L)
        return if (!ShizukuService.pingBinder()) {
            if (isShizukuV3Failed()) {
                // provider started with no binder included, binder calls blocked by SELinux or server dead, should never happened
                // notify user
            }

            // Shizuku v3 may not running, notify user
            false
        } else {
            // Shizuku v3 binder received
            true
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
}