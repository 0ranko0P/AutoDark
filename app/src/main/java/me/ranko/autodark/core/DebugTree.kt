package me.ranko.autodark.core

import timber.log.Timber

object DebugTree : Timber.DebugTree() {
    override fun createStackElementTag(element: StackTraceElement): String? {
        return element.methodName
    }
}