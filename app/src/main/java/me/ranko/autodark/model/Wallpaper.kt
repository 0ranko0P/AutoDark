package me.ranko.autodark.model

import com.android.wallpaper.model.LiveWallpaperInfo
import org.json.JSONObject

/**
 * Wallpaper details can be persisted
 * */
class Wallpaper private constructor(
        val liveWallpaper: Boolean,

        /**
         * ID of persisted wallpaper image
         *
         * Depends on wallpaper type, for normal wallpaper it's a bitmap hashcode,
         * for LiveWallpaper it's a ComponentName
         *
         * @see [com.android.wallpaper.asset.BitmapUtils]
         * @see [android.app.WallpaperManager.getWallpaperId]
         * @see [android.content.ComponentName]
         * */
        val id: String
) {

    fun toJsonString(): String = JSONObject().put("live", liveWallpaper).put("id", id).toString()

    override fun toString(): String = "Wallpaper(liveWallpaper=$liveWallpaper, id='$id')"

    companion object {
        @JvmStatic
        fun fromLiveWallpaper(liveWallpaper: LiveWallpaperInfo): Wallpaper {
            return Wallpaper(true, liveWallpaper.toJsonString())
        }

        @JvmStatic
        fun fromBitmap(bitmapId: String): Wallpaper = Wallpaper(false, bitmapId)

        @JvmStatic
        fun fromJson(json: String): Wallpaper {
            val jsonObj = JSONObject(json)
            val liveWallpaper = jsonObj.getBoolean("live")
            val id = jsonObj.getString("id")
            return Wallpaper(liveWallpaper, id)
        }
    }
}