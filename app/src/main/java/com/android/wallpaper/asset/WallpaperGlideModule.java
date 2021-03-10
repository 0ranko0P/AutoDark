package com.android.wallpaper.asset;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import com.android.wallpaper.asset.LiveWallpaperThumbAssetLoader.LiveWallpaperThumbAssetLoaderFactory;
import com.android.wallpaper.asset.CurrentWallpaperAssetVNLoader.CurrentWallpaperAssetVNLoaderFactory;
import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;

import java.io.InputStream;

import me.ranko.autodark.asset.FileAssetLoader;

/**
 * Provides configuration for Glide, such as specifying an internal disk cache size.
 */
@GlideModule
public final class WallpaperGlideModule extends AppGlideModule {

    private static final int WALLPAPER_DISK_CACHE_SIZE_BYTES = 100 * 1024 * 1024;

    @Override
    public void applyOptions(@NonNull Context context, GlideBuilder builder) {
        // Default Glide cache size is 250MB so make the wallpaper cache much smaller at 100MB.
        builder.setDiskCache(new InternalCacheDiskCacheFactory(
                context, WALLPAPER_DISK_CACHE_SIZE_BYTES));

        // Default # of bitmap pool screens is 4, so reduce to 2 to make room for the additional memory
        // consumed by tiling large images in preview and also the large bitmap consumed by the live
        // wallpaper for daily rotation.
        MemorySizeCalculator calculator = new MemorySizeCalculator.Builder(context)
                .setBitmapPoolScreens(2f)
                .setMemoryCacheScreens(1.2f)
                .build();
        builder.setMemorySizeCalculator(calculator);
        builder.setDefaultRequestOptions(
                new RequestOptions().format(DecodeFormat.PREFER_ARGB_8888));
    }

    @Override
    public void registerComponents(@NonNull Context context,@NonNull Glide glide, Registry registry) {
        registry.append(FileAsset.class, Bitmap.class, new FileAssetLoader.FileDescriptorAssetLoaderFactory())
                .append(LiveWallpaperThumbAsset.class, Drawable.class, new LiveWallpaperThumbAssetLoaderFactory())
                .append(CurrentWallpaperAssetVN.class, InputStream.class, new CurrentWallpaperAssetVNLoaderFactory());
    }
}