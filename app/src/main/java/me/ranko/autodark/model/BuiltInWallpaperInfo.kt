package me.ranko.autodark.model

import android.app.WallpaperManager
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.annotation.WorkerThread
import com.android.wallpaper.asset.Asset
import com.android.wallpaper.asset.BuiltInWallpaperAsset
import com.android.wallpaper.asset.BuiltInWallpaperAsset.BUILT_IN_WALLPAPER_ID
import com.android.wallpaper.module.WallpaperPersister.DEFAULT_COMPRESS_FORMAT
import com.android.wallpaper.module.WallpaperPersister.DEFAULT_COMPRESS_QUALITY
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class BuiltInWallpaperInfo : SystemWallpaperInfo(WallpaperManager.FLAG_SYSTEM, BUILT_IN_WALLPAPER_ID) {

    override fun getAsset(context: Context): Asset {
        if (mAsset == null) {
            mAsset = BuiltInWallpaperAsset(context)
        }
        return mAsset!!
    }

    @WorkerThread
    @Throws(IOException::class)
    override suspend fun persist(context: Context) {
        val outFile = PersistableWallpaper.getWallpaperFile(context, wallpaperId)
        export(context, outFile)
    }

    @WorkerThread
    @Throws(IOException::class)
    override suspend fun export(context: Context, outFile: File) {
        val manager = WallpaperManager.getInstance(context)
        val bitmap = (manager.getBuiltInDrawable(which) as BitmapDrawable).bitmap
        manager.forgetLoadedWallpaper()
        yield()
        FileOutputStream(outFile).use { outStream ->
            bitmap.compress(DEFAULT_COMPRESS_FORMAT, DEFAULT_COMPRESS_QUALITY, outStream)
        }
    }
}