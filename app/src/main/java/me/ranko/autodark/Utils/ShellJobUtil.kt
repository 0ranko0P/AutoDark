package me.ranko.autodark.Utils

import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.ranko.autodark.Exception.CommandExecuteError
import java.io.*

/**
 * Job util to execute command
 *
 * @author  0ranko0P
 * */
@SuppressWarnings("unused")
object ShellJobUtil {
    private const val MAX_BUFFER_LINE = 3000L

    @JvmStatic
    @WorkerThread
    suspend fun runSudoJob(command: String) {
        return runJob("su", "-c", command)
    }

    @JvmStatic
    @WorkerThread
    suspend fun runSudoJobForValue(command: String): String? {
        return runJobForValue("su", "-c", command)
    }

    /**
     * Execute command with result
     *
     * @return  Stdout put of subprocess
     *
     * @throws  CommandExecuteError
     *          When error occurred while execute command
     *          Or process ended exceptionally
     *
     * @see     Runtime.exec
     * */
    @JvmStatic
    @WorkerThread
    @Throws(CommandExecuteError::class)
    suspend fun runJobForValue(vararg commands: String): String? = withContext(Dispatchers.IO) {
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec(commands)
            val result = readStdout(process.inputStream, true)

            if (process.waitFor() != 0)
                throw CommandExecuteError(readStdout(process.errorStream, false))

            return@withContext result
        } catch (e: Exception) {
            throw CommandExecuteError(e)
        } finally {
            process?.destroy()
        }
    }

    /**
     * Execute non result command
     *
     * @see     Runtime.exec
     * */
    @WorkerThread
    @JvmStatic
    suspend fun runJob(vararg commands: String) = withContext(Dispatchers.IO) {
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec(commands)
            BufferedOutputStream(process.outputStream).use {
                it.write("exit\n".toByteArray())
                it.flush()
            }

            if (process.waitFor() != 0)
                throw CommandExecuteError(readStdout(process.errorStream, false))
        } catch (e: Exception) {
            throw CommandExecuteError(e)
        } finally {
            process?.destroy()
        }
    }

    /**
     * Read std out from a process
     * Maximum line to read is 3000
     *
     * @param   allLine set false if only read one line
     *
     * @see     MAX_BUFFER_LINE
     * */
    @JvmStatic
    @WorkerThread
    @Throws(IOException::class)
    fun readStdout(ins: InputStream, allLine: Boolean): String? {
        val sb = StringBuilder()
        BufferedReader(InputStreamReader(ins)).use {
            if (!allLine) return it.readLine()

            var line = 0L
            var tmp: String?

            while (line < MAX_BUFFER_LINE) {
                tmp = it.readLine()
                if (tmp == null) {
                    break
                } else {
                    line++
                    sb.append(tmp).append('\n')
                }
            }
        }
        // remove last LF char
        if (sb.isNotEmpty())
            sb.deleteCharAt(sb.length - 1)
        return sb.toString()
    }
}