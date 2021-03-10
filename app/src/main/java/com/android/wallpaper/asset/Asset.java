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
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;

import timber.log.Timber;

/**
 * Interface representing an image asset.
 *
 * [0ranko0P]: Make all asset receiver callback extends [ErrorReceiver].
 */
public abstract class Asset {

    /**
     * Creates and returns a placeholder Drawable instance sized exactly to the target ImageView and
     * filled completely with pixels of the provided placeholder color.
     */
    public static Drawable getPlaceholderDrawable(Context context, ImageView imageView,
                                                     @ColorInt int placeholderColor) {
        Point imageViewDimensions = getViewDimensions(imageView);
        Bitmap placeholderBitmap =
                Bitmap.createBitmap(imageViewDimensions.x, imageViewDimensions.y, Config.ARGB_8888);
        placeholderBitmap.eraseColor(placeholderColor);
        return new BitmapDrawable(context.getResources(), placeholderBitmap);
    }

    /**
     * Returns the visible height and width in pixels of the provided ImageView, or if it hasn't
     * been laid out yet, then gets the absolute value of the layout params.
     */
    public static Point getViewDimensions(View view) {
        int width = view.getWidth() > 0 ? view.getWidth() : Math.abs(view.getLayoutParams().width);
        int height = view.getHeight() > 0 ? view.getHeight()
                : Math.abs(view.getLayoutParams().height);

        return new Point(width, height);
    }

    /**
     * Decodes a bitmap sized for the destination view's dimensions off the main UI thread.
     *
     * @param targetWidth  Width of target view in physical pixels.
     * @param targetHeight Height of target view in physical pixels.
     * @param receiver     Called with the decoded bitmap or null if there was an error decoding the
     *                     bitmap.
     */
    public abstract void decodeBitmapAsync(int targetWidth, int targetHeight, BitmapReceiver receiver);

    @WorkerThread
    public abstract @NonNull Bitmap decodeBitmap(int targetWidth, int targetHeight) throws IOException;

    /**
     * Decodes and downscales a bitmap region off the main UI thread.
     *
     * @param rect         Rect representing the crop region in terms of the original image's
     *                     resolution.
     * @param targetWidth  Width of target view in physical pixels.
     * @param targetHeight Height of target view in physical pixels.
     * @param receiver     Called with the decoded bitmap region or null if there was an error
     *                     decoding the bitmap region.
     */
    public abstract void decodeBitmapRegionAsync(Rect rect, int targetWidth, int targetHeight,
                                            BitmapReceiver receiver);

    @WorkerThread
    public @NonNull abstract Bitmap decodeBitmapRegion(Rect rect, int targetWidth, int targetHeight)
            throws IOException, OutOfMemoryError;

    /**
     * Calculates the raw dimensions of the asset at its original resolution off the main UI thread.
     * Avoids decoding the entire bitmap if possible to conserve memory.
     *
     * @param receiver Called with the decoded raw dimensions of the whole image or null if there
     *                 was an error decoding the dimensions.
     */
    public abstract void decodeRawDimensionsAsync(DimensionsReceiver receiver);

    public abstract @NonNull Point decodeRawDimensions() throws IOException;

    /**
     * Interface for receiving decoded Bitmaps.
     */
    public interface BitmapReceiver extends ErrorReceiver {

        /**
         * Called with a decoded Bitmap object or null if there was an error decoding the bitmap.
         */
        void onBitmapDecoded(@NonNull Bitmap bitmap);
    }

    /**
     * Interface for receiving raw asset dimensions.
     */
    public interface DimensionsReceiver extends ErrorReceiver {

        /**
         * Called with raw dimensions of asset or null if the asset is unable to decode the raw
         * dimensions.
         *
         * @param dimensions Dimensions as a Point where width is represented by "x" and height by
         *                   "y".
         */
        void onDimensionsDecoded(@NonNull Point dimensions);
    }

    public interface ErrorReceiver {

        default void onError(@Nullable Exception e) {
            Timber.e(e);
        }
    }
}