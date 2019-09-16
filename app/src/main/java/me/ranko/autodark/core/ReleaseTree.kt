package me.ranko.autodark.core

import android.util.Log
import timber.log.Timber

object ReleaseTree : Timber.DebugTree() {

    override fun isLoggable(tag: String?, priority: Int): Boolean {
        return priority <=  Log.INFO
    }
}