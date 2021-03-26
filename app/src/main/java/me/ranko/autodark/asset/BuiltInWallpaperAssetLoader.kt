package me.ranko.autodark.asset

import android.graphics.Bitmap
import com.android.wallpaper.asset.BuiltInWallpaperAsset
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import java.io.IOException

class BuiltInWallpaperAssetLoader : ModelLoader<BuiltInWallpaperAsset, Bitmap> {

    override fun buildLoadData(model: BuiltInWallpaperAsset, width: Int, height: Int, options: Options): ModelLoader.LoadData<Bitmap> {
        return ModelLoader.LoadData(ObjectKey(model), BuiltInWallpaperAssetDataFetcher(model, width, height))
    }

    override fun handles(model: BuiltInWallpaperAsset): Boolean = true

    class BuiltInWallpaperAssetDataFetcher(private val asset: BuiltInWallpaperAsset,
                                    private val width: Int,
                                    private val height: Int) : DataFetcher<Bitmap> {

        override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
            return try {
                callback.onDataReady(asset.decodeBitmap(width, height))
            } catch (e: IOException) {
                callback.onLoadFailed(e)
            }
        }

        override fun getDataClass(): Class<Bitmap> = Bitmap::class.java

        override fun getDataSource(): DataSource = DataSource.LOCAL

        override fun cancel() {
            // no-op
        }

        override fun cleanup() {
            // no-op
        }
    }

    class BuiltInWallpaperAssetLoaderFactory : ModelLoaderFactory<BuiltInWallpaperAsset, Bitmap> {

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<BuiltInWallpaperAsset, Bitmap> {
            return BuiltInWallpaperAssetLoader()
        }

        override fun teardown() {
            // no-op
        }
    }
}