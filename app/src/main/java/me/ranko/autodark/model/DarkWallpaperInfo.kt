package me.ranko.autodark.model

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import com.android.wallpaper.asset.Asset
import com.android.wallpaper.asset.FileAsset
import com.android.wallpaper.model.WallpaperInfo

/**
 * Represents a wallpaper in storage and it's ready for the dark mode.
 * */
class DarkWallpaperInfo(private val id: String) : WallpaperInfo() {

    companion object CREATOR : Parcelable.Creator<DarkWallpaperInfo> {
        override fun createFromParcel(parcel: Parcel): DarkWallpaperInfo {
            return DarkWallpaperInfo(parcel)
        }

        override fun newArray(size: Int): Array<DarkWallpaperInfo?> {
            return arrayOfNulls(size)
        }
    }

    private var mAsset: FileAsset? = null

    constructor(parcel: Parcel) : this(parcel.readString()!!)

    override fun getAsset(context: Context): Asset {
        if (mAsset == null) {
            mAsset = FileAsset(PersistableWallpaper.getWallpaperFile(context, id))
        }
        return mAsset!!
    }

    override fun getWallpaperId(): String = id

    override fun getThumbAsset(context: Context): Asset? = null

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(id)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return (other is DarkWallpaperInfo && id == other.id)
    }

    override fun hashCode(): Int = id.hashCode()
}