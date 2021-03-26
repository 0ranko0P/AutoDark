/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.wallpaper.asset;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import com.android.wallpaper.util.TaskRunner;
import com.android.wallpaper.util.TaskRunner.Callback;

import org.jetbrains.annotations.NotNull;

/**
 * Asset representing the system's built-in wallpaper.
 * NOTE: This is only used for KitKat and newer devices. On older versions of Android, the
 * built-in wallpaper is accessed via the system Resources object, and is thus be represented
 * by a {@code ResourceAsset} instead.
 *
 * [0ranko0P] changes:
 *     0. Hardcode id and hashcode.
 *     1. Replace AsyncTask with Callable.
 */
public final class BuiltInWallpaperAsset extends Asset {
    private static final boolean SCALE_TO_FIT = true;
    private static final boolean CROP_TO_FIT = false;
    private static final float HORIZONTAL_CENTER_ALIGNED = 0.5f;
    private static final float VERTICAL_CENTER_ALIGNED = 0.5f;

    public static final int BUILT_IN_WALLPAPER_ID = 0;
    public static final int BUILT_IN_WALLPAPER_HASHCODE = 114514;

    private final Context mContext;

    private Point mDimensions;

    /**
     * @param context The application's context.
     */
    public BuiltInWallpaperAsset(Context context) {
        mContext = context.getApplicationContext();
    }

    @Override
    public void decodeBitmapRegionAsync(Rect rect, int targetWidth, int targetHeight, Callback<Bitmap> receiver) {
        TaskRunner.getINSTANCE().executeAsync(() -> decodeBitmapRegion(rect, targetWidth, targetHeight), receiver);
    }

    @NonNull
    @Override
    public Bitmap decodeBitmapRegion(Rect rect, int targetWidth, int targetHeight) throws OutOfMemoryError {
        Point dimensions = decodeRawDimensions();

        float horizontalCenter = BitmapUtils.calculateHorizontalAlignment(dimensions, rect);
        float verticalCenter = BitmapUtils.calculateVerticalAlignment(dimensions, rect);

        Drawable drawable = WallpaperManager.getInstance(mContext).getBuiltInDrawable(
                rect.width(),
                rect.height(),
                CROP_TO_FIT,
                horizontalCenter,
                verticalCenter);

        return ((BitmapDrawable) drawable).getBitmap();
    }

    @Override
    public void decodeRawDimensionsAsync(DimensionsReceiver receiver) {
        TaskRunner.getINSTANCE().executeAsync(this::decodeRawDimensions, new Callback<Point>() {
            @Override
            public void onComplete(Point dimensions) {
                receiver.onDimensionsDecoded(dimensions);
            }

            @Override
            public void onError(Exception e) {
                receiver.onError(e);
            }
        });
    }

    @Override
    public void decodeBitmapAsync(int targetWidth, int targetHeight, Callback<Bitmap> receiver) {
        TaskRunner.getINSTANCE().executeAsync(() -> decodeBitmap(targetWidth, targetHeight), receiver);
    }

    @NonNull
    @Override
    public Bitmap decodeBitmap(int targetWidth, int targetHeight) {
        final WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);

        Drawable drawable = wallpaperManager.getBuiltInDrawable(
                targetWidth,
                targetHeight,
                SCALE_TO_FIT,
                HORIZONTAL_CENTER_ALIGNED,
                VERTICAL_CENTER_ALIGNED);

        // Manually request that WallpaperManager loses its reference to the built-in wallpaper
        // bitmap, which can occupy a large memory allocation for the lifetime of the app.
        wallpaperManager.forgetLoadedWallpaper();

        return ((BitmapDrawable) drawable).getBitmap();
    }

    /**
     * Calculates the raw dimensions of the built-in drawable. This method should not be called from
     * the main UI thread.
     *
     * @return Raw dimensions of the built-in wallpaper drawable.
     */
    @Override
    public @NotNull Point decodeRawDimensions() {
        if (mDimensions != null) {
            return mDimensions;
        }

        Drawable builtInDrawable = WallpaperManager.getInstance(mContext).getBuiltInDrawable();
        Bitmap builtInBitmap = ((BitmapDrawable) builtInDrawable).getBitmap();
        mDimensions = new Point(builtInBitmap.getWidth(), builtInBitmap.getHeight());
        return mDimensions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof BuiltInWallpaperAsset;
    }

    @Override
    public int hashCode() {
        return BUILT_IN_WALLPAPER_HASHCODE;
    }

    @Override
    public String toString() {
        return "BuiltInWallpaperAsset{" + BUILT_IN_WALLPAPER_HASHCODE + '}';
    }
}