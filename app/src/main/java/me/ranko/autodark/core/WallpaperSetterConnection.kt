package me.ranko.autodark.core

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.android.wallpaper.asset.StreamableAsset
import com.android.wallpaper.model.LiveWallpaperInfo
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.module.WallpaperPersister
import com.android.wallpaper.module.WallpaperSetter

class WallpaperSetterConnection(
    private val context: Context,
    private val wallpapers: Pair<WallpaperInfo, WallpaperInfo?>,
    private val callback: WallpaperPersister.SetWallpaperCallback,
    private val setter: WallpaperSetter
) : ServiceConnection, WallpaperSetterBinder.WallpaperSetterServiceCallback,
    WallpaperPersister.SetWallpaperCallback {

    private var binder: WallpaperSetterBinder? = null

    constructor(
        context: Context,
        wallpaper: LiveWallpaperInfo,
        callback: WallpaperPersister.SetWallpaperCallback,
        setter: WallpaperSetter
    ) : this(context, Pair(wallpaper, null), callback, setter)

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        binder = (service as WallpaperSetterBinder)
        binder!!.start(this)
    }

    override fun onServiceDisconnected(name: ComponentName) {
        // no-op
    }

    override fun onReadyToSet() {
        val home = wallpapers.first
        if (home is LiveWallpaperInfo) {
            setter.setCurrentLiveWallpaper(home, this)
        } else {
            val lockAsset: StreamableAsset? = wallpapers.second?.getAsset(context)?.let {
                it as StreamableAsset
            }
            setter.setDarkWallpapers(home.getAsset(context) as StreamableAsset, lockAsset, this)
        }
    }

    override fun onServiceFailure(e: Exception) {
        this.onError(e)
    }

    override fun onSuccess(id: String) {
        callback.onSuccess(id)
        destroy()
    }

    override fun onError(e: java.lang.Exception?) {
        super.onError(e)
        callback.onError(e)
        destroy()
    }

    private fun destroy() {
        context.unbindService(this)
        binder!!.destroy()
    }
}