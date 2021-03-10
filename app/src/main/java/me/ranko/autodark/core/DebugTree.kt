package me.ranko.autodark.core

import timber.log.Timber

open class DebugTree : Timber.DebugTree() {
    override fun createStackElementTag(element: StackTraceElement): String? {
        return String.format("(%s:%s)#%s", element.fileName, element.lineNumber, element.methodName)
    }
}