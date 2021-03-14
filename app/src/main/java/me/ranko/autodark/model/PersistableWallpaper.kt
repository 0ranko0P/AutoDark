package me.ranko.autodark.model

import android.content.Context
import androidx.annotation.WorkerThread
import java.io.File
import java.io.IOException

interface PersistableWallpaper {

    companion object {
        private const val DEFAULT_WALLPAPER_FOLDER = "Wallpapers"

        fun getWallpaperFile(context: Context, id: String): File {
            return File(context.getFileStreamPath(DEFAULT_WALLPAPER_FOLDER), id)
        }
    }

    /**
     * Persist this wallpaper to storage
     * */
    @WorkerThread
    @Throws(IOException::class)
    suspend fun persist(context: Context)

    /**
     * @return **true** If this Persistable haven't save to storage yet.
     * */
    fun isNew(context: Context): Boolean

    /**
     * Delete from storage
     * */
    fun delete(context: Context): Boolean
}