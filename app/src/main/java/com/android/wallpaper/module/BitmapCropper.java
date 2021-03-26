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
package com.android.wallpaper.module;

import android.graphics.Bitmap;
import android.graphics.Rect;

import androidx.annotation.NonNull;

import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.StreamableAsset;
import com.android.wallpaper.util.TaskRunner;

import java.util.concurrent.Callable;

/**
 * Default implementation of BitmapCropper, which actually crops and scales bitmaps.
 *
 * * [0ranko0P]: Replace AsyncTask with Callable
 */
public final class BitmapCropper {
    private static final boolean FILTER_SCALED_BITMAP = true;

    private BitmapCropper() {
        throw new UnsupportedOperationException();
    }

    /**
     * Interface for receiving the output bitmap of crop operations.
     */
    public interface Callback extends Asset.ErrorReceiver {
        void onBitmapCropped(@NonNull Bitmap croppedBitmap);
    }

    public static void cropAndScaleBitmapAsync(Asset asset, float scale, final Rect cropRect,
                                   final Callback callback) {
        // Crop rect in pixels of source image.
        Rect scaledCropRect = new Rect(
                Math.round((float) cropRect.left / scale),
                Math.round((float) cropRect.top / scale),
                Math.round((float) cropRect.right / scale),
                Math.round((float) cropRect.bottom / scale));

        asset.decodeBitmapRegionAsync(scaledCropRect, cropRect.width(), cropRect.height(), new TaskRunner.Callback<Bitmap>() {
            @Override
            public void onComplete(@NonNull Bitmap bitmap) {
                // UI won't decode it anymore, it's useless now
                // prepare for next scale task
                if (asset instanceof StreamableAsset) {
                    ((StreamableAsset) asset).recycle();
                }
                // Asset provides a bitmap which is appropriate for the target width & height, but since
                // it does not guarantee an exact size we need to fit the bitmap to the cropRect.
                ScaleBitmapTask task = new ScaleBitmapTask(bitmap, cropRect);
                TaskRunner.getINSTANCE().executeIOAsync(task, new TaskRunner.Callback<Bitmap>() {
                    @Override
                    public void onComplete(Bitmap croppedBitmap) {
                        callback.onBitmapCropped(croppedBitmap);
                    }

                    @Override
                    public void onError(Exception e) {
                        callback.onError(e);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    /**
     * Callable subclass which creates a new bitmap which is resized to the exact dimensions of a
     * Rect using Bitmap#createScaledBitmap.
     */
    private static final class ScaleBitmapTask implements Callable<Bitmap> {

        private final Rect mCropRect;

        private final Bitmap mBitmap;

        public ScaleBitmapTask(@NonNull Bitmap bitmap, Rect cropRect) {
            super();
            mBitmap = bitmap;
            mCropRect = cropRect;
        }

        @Override
        public Bitmap call() throws OutOfMemoryError {
            // Fit bitmap to exact dimensions of crop rect.
            return Bitmap.createScaledBitmap(
                    mBitmap,
                    mCropRect.width(),
                    mCropRect.height(),
                    FILTER_SCALED_BITMAP);
        }
    }
}