package me.ranko.autodark.core

import android.util.Log

object ReleaseTree : DebugTree() {

    override fun isLoggable(tag: String?, priority: Int): Boolean {
        return priority >=  Log.INFO
    }
}