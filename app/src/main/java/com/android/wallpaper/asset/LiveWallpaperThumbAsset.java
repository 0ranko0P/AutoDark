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

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.wallpaper.util.TaskRunner.Callback;

import java.io.IOException;
import java.util.Objects;

import timber.log.Timber;

/**
 * Asset wrapping a drawable for a live wallpaper thumbnail.
 *
 * [0ranko0P] changes:
 *     0. Throw exception when decode this asset.
 *     1. Drop glide key implementation
 */
public class LiveWallpaperThumbAsset extends Asset {

    protected final Context mContext;
    protected final android.app.WallpaperInfo mInfo;
    // The content Uri of thumbnail
    protected Uri mUri;
    private BitmapDrawable mThumbnailDrawable;

    public LiveWallpaperThumbAsset(Context context, android.app.WallpaperInfo info) {
        mContext = context.getApplicationContext();
        mInfo = info;
    }

    public LiveWallpaperThumbAsset(Context context, android.app.WallpaperInfo info, Uri uri) {
        this(context, info);
        mUri = uri;
    }

    @Override
    public void decodeBitmapAsync(int targetWidth, int targetHeight, Callback<Bitmap> receiver) {
        throw new UnsupportedOperationException();
    }

    @NonNull
    @Override
    public Bitmap decodeBitmap(int targetWidth, int targetHeight) {
        throw new UnsupportedOperationException();
    }

    @Override
    public @NonNull Bitmap decodeBitmapRegion(Rect rect, int targetWidth, int targetHeight) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void decodeBitmapRegionAsync(Rect rect, int targetWidth, int targetHeight,
                                        Callback<Bitmap> receiver) {
        receiver.onError(new UnsupportedOperationException());
    }

    @Override
    public void decodeRawDimensionsAsync(DimensionsReceiver receiver) {
        throw new UnsupportedOperationException();
    }

    public @NonNull Point decodeRawDimensions() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the thumbnail drawable for the live wallpaper synchronously. Should not be called on
     * the main UI thread.
     */
    public @Nullable Drawable getThumbnailDrawable() {
        if (mUri != null) {
            if (mThumbnailDrawable != null) {
                return mThumbnailDrawable;
            }
            try {
                AssetFileDescriptor assetFileDescriptor =
                        mContext.getContentResolver().openAssetFileDescriptor(mUri, "r");
                if (assetFileDescriptor != null) {
                    mThumbnailDrawable = new BitmapDrawable(mContext.getResources(),
                            BitmapFactory.decodeStream(assetFileDescriptor.createInputStream()));
                    return mThumbnailDrawable;
                }
            } catch (IOException e) {
                Timber.e( "Not found thumbnail from URI.");
            }
        }
        return mInfo.loadThumbnail(mContext.getPackageManager());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LiveWallpaperThumbAsset that = (LiveWallpaperThumbAsset) o;
        return Objects.equals(mInfo.getPackageName(), that.mInfo.getPackageName()) &&
                Objects.equals(mInfo.getServiceName(), that.mInfo.getServiceName());
    }

    @Override
    public int hashCode() { // ComponentName.hashCode
        return mInfo.getPackageName().hashCode() + mInfo.getServiceName().hashCode();
    }

    public @NonNull String toString() {
        return "LiveWallpaperThumbAsset{"
                + "packageName=" + mInfo.getPackageName() + ","
                + "serviceName=" + mInfo.getServiceName()
                + '}';
    }
}