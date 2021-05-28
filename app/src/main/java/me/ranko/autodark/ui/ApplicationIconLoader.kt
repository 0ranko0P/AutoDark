package me.ranko.autodark.ui

import android.content.Context
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
import me.ranko.autodark.model.BlockableApplication

class ApplicationIconLoader(val packageManager: PackageManager) : ModelLoader<BlockableApplication, Drawable> {

    override fun buildLoadData(model: BlockableApplication, width: Int, height: Int, options: Options): ModelLoader.LoadData<Drawable> {
        return ModelLoader.LoadData(ObjectKey(model), ApplicationIconDataFetcher(packageManager, model))
    }

    override fun handles(model: BlockableApplication): Boolean = true

    class ApplicationIconDataFetcher(private val packageManager: PackageManager,
                                     private val app: BlockableApplication): DataFetcher<Drawable> {
        override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Drawable>) {
            val icon = packageManager.getApplicationIcon(app)
            if (app.isPrimaryUser()) {
                callback.onDataReady(icon)
            } else {
                callback.onDataReady(packageManager.getUserBadgedIcon(icon, app.user!!))
            }
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

    class ApplicationIconFactory(context: Context): ModelLoaderFactory<BlockableApplication, Drawable> {

        private val packageManager: PackageManager = context.applicationContext.packageManager

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<BlockableApplication, Drawable> {
            return ApplicationIconLoader(packageManager)
        }

        override fun teardown() {
            // no-op
        }
    }
}