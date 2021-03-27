package me.ranko.autodark.model

import android.annotation.SuppressLint
import android.content.Context
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import androidx.annotation.WorkerThread
import com.android.wallpaper.asset.Asset
import com.android.wallpaper.asset.CurrentWallpaperAssetVN
import com.android.wallpaper.model.WallpaperInfo
import kotlinx.coroutines.yield
import me.ranko.autodark.model.PersistableWallpaper.Companion.getWallpaperFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel

@SuppressLint("MissingPermission")
open class SystemWallpaperInfo(protected val which: Int, val id: Int) : WallpaperInfo(), PersistableWallpaper {

    companion object CREATOR : Parcelable.Creator<SystemWallpaperInfo> {
        override fun createFromParcel(parcel: Parcel): SystemWallpaperInfo {
            return SystemWallpaperInfo(parcel)
        }

        override fun newArray(size: Int): Array<SystemWallpaperInfo?> {
            return arrayOfNulls(size)
        }
    }

    protected var mAsset: Asset? = null

    constructor(parcel: Parcel) : this(parcel.readInt(), parcel.readInt())

    override fun getAsset(context: Context): Asset {
        if (mAsset == null) {
            val asset = CurrentWallpaperAssetVN(context, which)
            if (asset.id == id) {
                this.mAsset = asset
            } else {
                throw IllegalStateException("InValid wallpaper info!")
            }
        }

        return mAsset!!
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(which)
        dest.writeInt(id)
    }

    override fun getThumbAsset(context: Context): Asset?  = null

    @WorkerThread
    @Throws(IOException::class)
    override suspend fun persist(context: Context) {
        export(context, getWallpaperFile(context, wallpaperId))
    }

    @WorkerThread
    @Throws(IOException::class)
    open suspend fun export(context: Context, outFile: File) {
        val asset = getAsset(context) as CurrentWallpaperAssetVN
        yield()
        ParcelFileDescriptor.AutoCloseInputStream(asset.getWallpaperPfd()).use { ins ->
            FileOutputStream(outFile).use { des ->
                val source: FileChannel = ins.channel
                des.channel.transferFrom(source, 0, source.size())
            }
        }
    }

    override fun isNew(context: Context): Boolean = getWallpaperFile(context, wallpaperId).exists().not()

    override fun getWallpaperId(): String = id.toString()

    override fun delete(context: Context): Boolean =  getWallpaperFile(context, wallpaperId).delete()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        return (other is SystemWallpaperInfo && other.id == id)
    }

    override fun hashCode(): Int =  id
}