package me.ranko.autodark.Utils

import android.annotation.SuppressLint
import android.util.Log
import androidx.annotation.WorkerThread
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions

@SuppressLint("LogNotTimber")
object FileUtil {

    private const val TAG = "FileUtil"

    @JvmStatic
    val PERMISSION_755 = "rwxr-xr-x"

    @JvmStatic
    val PERMISSION_744 = "rwxr--r--"

    @JvmStatic
    fun chgrop(path: Path, group: String) {
        val groupPrincipal = FileSystems.getDefault().userPrincipalLookupService.lookupPrincipalByGroupName(group)
        Files.getFileAttributeView(path, PosixFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS).setGroup(groupPrincipal)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun chmod(path: Path, permissionNum: String) {
        val permissions = PosixFilePermissions.fromString(permissionNum)
        Files.setPosixFilePermissions(path, permissions)
    }

    /**
     * @return **True** target path is newly created
     * */
    @JvmStatic
    @Throws(IOException::class)
    fun createIfNotExists(path: Path, isFolder: Boolean, permissionNum: String): Boolean {
        if (path.toFile().exists()) return false

        val permissions = PosixFilePermissions.fromString(permissionNum)
        val attr = PosixFilePermissions.asFileAttribute(permissions)
        if (isFolder) {
            Files.createDirectory(path, attr)
        } else {
            Files.createFile(path, attr)
        }
        Files.setPosixFilePermissions(path, permissions)
        return true
    }

    @JvmStatic
    @WorkerThread
    fun readList(path: Path): List<String>? {
        if (Files.exists(path).not()) {
            Log.w(TAG,"onReadList: File not exists or readable: $path")
            return null
        }

        return try {
            Files.readAllLines(path)
        } catch (e: Exception) {
            Log.w(TAG, "Read list failed", e)
            null
        }
    }


    @JvmStatic
    @WorkerThread
    fun saveFlagAsFile(flagPath: Path, setFlag: Boolean): Boolean {
        return try {
            if (setFlag) {
                if (createIfNotExists(flagPath, false, PERMISSION_744)) {
                    chmod(flagPath.parent, PERMISSION_755)
                }
            } else {
                Files.deleteIfExists(flagPath)
            }
            true
        } catch (e: IOException) {
            Log.w(TAG, e)
            false
        }
    }
}