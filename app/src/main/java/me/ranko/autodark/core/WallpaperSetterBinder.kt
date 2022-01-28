package me.ranko.autodark.core

import android.os.Binder

abstract class WallpaperSetterBinder : Binder() {

    interface WallpaperSetterServiceCallback {
        fun onReadyToSet()

        fun onServiceFailure(e: Exception)
    }

    abstract fun start(callback: WallpaperSetterServiceCallback)

    abstract fun destroy()
}