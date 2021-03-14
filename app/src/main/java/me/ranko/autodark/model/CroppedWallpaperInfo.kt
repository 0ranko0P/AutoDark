package me.ranko.autodark.model

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.WorkerThread
import com.android.wallpaper.asset.Asset
import com.android.wallpaper.asset.FileAsset
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.module.WallpaperPersister.Destination
import me.ranko.autodark.model.PersistableWallpaper.Companion.getWallpaperFile
import java.io.File
import java.io.IOException
import java.util.Objects

/**
 * Represents a cropped wallpaper cached in [Context.getCacheDir]
 * */
class CroppedWallpaperInfo(val id: String, @Destination val destination: Int) : WallpaperInfo(), PersistableWallpaper {

    companion object CREATOR : Parcelable.Creator<CroppedWallpaperInfo> {
        override fun createFromParcel(parcel: Parcel): CroppedWallpaperInfo {
            return CroppedWallpaperInfo(parcel)
        }

        override fun newArray(size: Int): Array<CroppedWallpaperInfo?> {
            return arrayOfNulls(size)
        }
    }

    private var cacheAsset: FileAsset? = null

    private var fileAsset: FileAsset? = null

    constructor(parcel: Parcel) : this(parcel.readString()!!, parcel.readInt())

    override fun isNew(context: Context): Boolean {
        getAsset(context)
        // check for non-cache file status
        return fileAsset!!.mFile.exists().not()
    }

    override fun delete(context: Context): Boolean {
        if (isNew(context).not()) {
            return fileAsset!!.mFile.delete()
        }
        return true
    }

    override fun getAsset(context: Context): Asset {
        if (cacheAsset == null) {
            cacheAsset = FileAsset(File(context.cacheDir, id))
            fileAsset = FileAsset(getWallpaperFile(context, id))
        }
        return cacheAsset!!
    }

    @WorkerThread
    @Throws(IOException::class)
    override suspend fun persist(context: Context) {
        val cacheFile = (getAsset(context) as FileAsset).mFile

        if (cacheFile.renameTo(fileAsset!!.mFile).not()) {
            throw IOException("Failed to persist wallpaper: Id:$id, $cacheFile Exists: ${cacheFile.exists()}")
        }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeInt(destination)
    }

    override fun getThumbAsset(context: Context?): Asset?  = null

    override fun describeContents(): Int = 0

    override fun getWallpaperId(): String = id

    override fun toString(): String =
        "CroppedWallpaperInfo(id='$id', destination=$destination, cacheAsset=$cacheAsset)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        return (other is CroppedWallpaperInfo && other.id == id && other.destination == destination)
    }

    override fun hashCode(): Int = Objects.hash(id, destination)
}