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
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.Asset.BitmapReceiver;
import com.android.wallpaper.asset.StreamableAsset;

import java.io.IOException;

import timber.log.Timber;

/**
 * Default implementation of BitmapCropper, which actually crops and scales bitmaps.
 */
public final class BitmapCropper {
    private static final boolean FILTER_SCALED_BITMAP = true;

    private BitmapCropper() {
        throw new UnsupportedOperationException();
    }

    /**
     * Interface for receiving the output bitmap of crop operations.
     */
    public interface Callback extends Asset.ErrorReceiver{
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

        asset.decodeBitmapRegionAsync(scaledCropRect, cropRect.width(), cropRect.height(), new BitmapReceiver() {
            @Override
            public void onBitmapDecoded(@NonNull Bitmap bitmap) {
                // UI won't decode it anymore, it's useless now
                // prepare for next scale task
                if (asset instanceof StreamableAsset) {
                    ((StreamableAsset) asset).recycle();
                }
                // Asset provides a bitmap which is appropriate for the target width & height, but since
                // it does not guarantee an exact size we need to fit the bitmap to the cropRect.
                ScaleBitmapTask task = new ScaleBitmapTask(bitmap, cropRect, callback);
                task.execute();
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    /**
     * AsyncTask subclass which creates a new bitmap which is resized to the exact dimensions of a
     * Rect using Bitmap#createScaledBitmap.
     */
    private static final class ScaleBitmapTask extends AsyncTask<Void, Void, Boolean> {

        private final Rect mCropRect;
        private final Callback mCallback;

        @Nullable
        private Exception exception;

        private Bitmap mBitmap;

        public ScaleBitmapTask(@NonNull Bitmap bitmap, Rect cropRect, Callback callback) {
            super();
            mBitmap = bitmap;
            mCropRect = cropRect;
            mCallback = callback;
        }

        @Override
        protected Boolean doInBackground(Void... unused) {
            try {
                // Fit bitmap to exact dimensions of crop rect.
                mBitmap = Bitmap.createScaledBitmap(
                        mBitmap,
                        mCropRect.width(),
                        mCropRect.height(),
                        FILTER_SCALED_BITMAP);
                return true;
            } catch (OutOfMemoryError e) {
                Timber.w(e, "Not enough memory to fit the final cropped and scaled bitmap to size" );
                exception = new IOException(e);
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean isSuccess) {
            if (isSuccess) {
                mCallback.onBitmapCropped(mBitmap);
            } else {
                mCallback.onError(exception);
            }
        }
    }
}