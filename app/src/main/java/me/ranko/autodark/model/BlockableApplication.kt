package me.ranko.autodark.model

import android.content.pm.ApplicationInfo
import android.os.Process
import android.os.UserHandle

class BlockableApplication(
    app: ApplicationInfo,
    val user: UserHandle? = null,
    val userId: Int = Process.ROOT_UID,
) : ApplicationInfo(app), Blockable {

    override fun getPackageName(): String = packageName

    override fun isPrimaryUser(): Boolean = user == null || userId == Process.ROOT_UID

    fun isSysApp(): Boolean = flags.and(FLAG_SYSTEM) != 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is Blockable && packageName == other.getPackageName()
    }

    override fun hashCode(): Int = packageName.hashCode()

    override fun toString(): String = packageName
}