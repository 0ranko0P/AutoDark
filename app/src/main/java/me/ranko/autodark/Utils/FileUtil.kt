package me.ranko.autodark.Utils

import android.util.Log
import androidx.annotation.WorkerThread
import me.ranko.autodark.Constant
import me.ranko.autodark.Receivers.ActivityUpdateReceiver
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions

object FileUtil {

    @JvmStatic
    val PERMISSION_770 = "rwxrwx---"
    @JvmStatic
    val PERMISSION_764 = "rwxrw-r--"

    @JvmStatic
    fun chgrop(path: Path, group: String) {
        val groupPrincipal = FileSystems.getDefault().userPrincipalLookupService.lookupPrincipalByGroupName(group)
        Files.getFileAttributeView(path, PosixFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS).setGroup(groupPrincipal)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun chmod(path: Path, permissionNum: String = PERMISSION_770) {
        val permissions = PosixFilePermissions.fromString(permissionNum)
        Files.setPosixFilePermissions(path, permissions)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun crateIfNotExists(path:Path, permissionNum: String = PERMISSION_770) {
        if(path.toFile().exists()) return

        val permissions = PosixFilePermissions.fromString(permissionNum)
        val attr = PosixFilePermissions.asFileAttribute(permissions)
        Files.createFile(path, attr)
        Files.setPosixFilePermissions(path, permissions)
    }

    @JvmStatic
    @WorkerThread
    fun readList(path: Path, knowSize: Int = 16): ArrayList<String>? {
        if (!path.toFile().exists()) {
            println("onReadList: File not exists or readable: $path")
            return null
        }

        val list = ArrayList<String>(knowSize)
        try {
            Files.readAllLines(Constant.BLOCK_LIST_PATH).forEach {
                list.add(it)
            }
            return list
        } catch (e: Exception) {
            println("onReadList:${Log.getStackTraceString(e)}")
            return null
        }
    }
}