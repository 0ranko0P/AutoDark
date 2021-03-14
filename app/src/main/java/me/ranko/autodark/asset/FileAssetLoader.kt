package me.ranko.autodark.asset

import android.graphics.Bitmap
import com.android.wallpaper.asset.FileAsset
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import java.io.IOException

class FileAssetLoader : ModelLoader<FileAsset, Bitmap> {

    override fun buildLoadData(model: FileAsset, width: Int, height: Int, options: Options): ModelLoader.LoadData<Bitmap> {
        return ModelLoader.LoadData(ObjectKey(model), FileDescriptorDataFetcher(model, width, height))
    }

    override fun handles(model: FileAsset): Boolean = true

    class FileDescriptorDataFetcher(private val asset: FileAsset,
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

        override fun cleanup() {
            asset.recycle()
        }

        override fun getDataSource(): DataSource = DataSource.LOCAL

        override fun cancel() {
            // no-op
        }
    }

    class FileDescriptorAssetLoaderFactory : ModelLoaderFactory<FileAsset, Bitmap> {

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<FileAsset, Bitmap> {
            return FileAssetLoader()
        }

        override fun teardown() {
            // no-op
        }
    }
}