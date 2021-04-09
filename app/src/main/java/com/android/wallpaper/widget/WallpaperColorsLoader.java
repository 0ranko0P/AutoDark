/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.wallpaper.widget;

import android.app.WallpaperColors;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.util.LruCache;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.LiveWallpaperThumbAsset;
import com.android.wallpaper.util.ScreenSizeCalculator;
import com.android.wallpaper.util.TaskRunner;

import timber.log.Timber;

/** A class to load the {@link WallpaperColors} from wallpaper {@link Asset}. */
public final class WallpaperColorsLoader {

    /** Callback of loading a {@link WallpaperColors}. */
    public interface Callback extends Asset.ErrorReceiver{
        /** Gets called when a {@link WallpaperColors} is loaded. */
        void onLoaded(@Nullable WallpaperColors colors);
    }

    // The max size should be at least 2 for storing home and lockscreen wallpaper if they are
    // different.
    private static final LruCache<Asset, WallpaperColors> sCache = new LruCache<>(/* maxSize= */ 8);

    /** Gets the {@link WallpaperColors} from the wallpaper {@link Asset}. */
    public static void getWallpaperColors(Context context, @NonNull Asset asset,
                                          @NonNull Callback callback) {
        WallpaperColors cached = sCache.get(asset);
        if (cached != null) {
            callback.onLoaded(cached);
            return;
        }

        if (asset instanceof LiveWallpaperThumbAsset) {
            Drawable drawable = ((LiveWallpaperThumbAsset) asset).getThumbnailDrawable();
            if (drawable == null) {
                Timber.d("Can't get wallpaper colors from null drawable, uses null color.");
                callback.onLoaded(null);
            } else {
                WallpaperColors colors = WallpaperColors.fromDrawable(drawable);
                sCache.put(asset, colors);
                callback.onLoaded(colors);
            }
            return;
        }

        Display display = context.getSystemService(WindowManager.class).getDefaultDisplay();
        Point screen = ScreenSizeCalculator.getInstance().getScreenSize(display);
        asset.decodeBitmapAsync(screen.y / 2, screen.x / 2, new TaskRunner.Callback<Bitmap>() {
            @Override
            public void onComplete(@NonNull Bitmap bitmap) {
                boolean shouldRecycle = false;
                if (bitmap.getConfig() == Bitmap.Config.HARDWARE) {
                    bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                    shouldRecycle = true;
                }
                WallpaperColors colors = WallpaperColors.fromBitmap(bitmap);
                sCache.put(asset, colors);
                callback.onLoaded(colors);
                if (shouldRecycle) {
                    bitmap.recycle();
                }
            }

            @Override
            public void onError(@Nullable Exception e) {
                Timber.d(e, "Can't get wallpaper colors from a null bitmap, uses null color.");
                callback.onError(e);
            }});
    }
}