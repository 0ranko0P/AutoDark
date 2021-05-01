package me.ranko.autodark.model

import android.content.pm.ApplicationInfo
import android.os.UserHandle

class UserApplicationInfo(app: ApplicationInfo,
                          val user: UserHandle,
                          val userId: Int) : ApplicationInfo(app) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserApplicationInfo) return false

        return packageName == other.packageName && user == other.user
    }

    override fun hashCode(): Int = user.hashCode() + packageName.hashCode()
}