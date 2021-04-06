package me.ranko.autodark.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey

class ApplicationIconLoader(val packageManager: PackageManager) : ModelLoader<ApplicationInfo, Drawable> {

    override fun buildLoadData(model: ApplicationInfo, width: Int, height: Int, options: Options): ModelLoader.LoadData<Drawable> {
        return ModelLoader.LoadData(ObjectKey(model), ApplicationIconDataFetcher(packageManager, model))
    }

    override fun handles(model: ApplicationInfo): Boolean = true

    class ApplicationIconDataFetcher(private val packageManager: PackageManager,
                                     private val app: ApplicationInfo): DataFetcher<Drawable> {
        override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Drawable>) {
            callback.onDataReady(packageManager.getApplicationIcon(app))
        }

        override fun cleanup() {
            // no-op
        }

        override fun cancel() {
            // no-op
        }

        override fun getDataClass(): Class<Drawable> = Drawable::class.java

        override fun getDataSource(): DataSource = DataSource.LOCAL
    }

    class ApplicationIconFactory(context: Context): ModelLoaderFactory<ApplicationInfo, Drawable> {

        private val packageManager: PackageManager = context.applicationContext.packageManager

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<ApplicationInfo, Drawable> {
            return ApplicationIconLoader(packageManager)
        }

        override fun teardown() {
            // no-op
        }
    }
}