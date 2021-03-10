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
package com.android.wallpaper.util;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Color;
import android.graphics.Point;

/**
 * Applies fill and stretch transformations to bitmaps.
 */
public final class BitmapTransformer {

    // Suppress default constructor for noninstantiability.
    private BitmapTransformer() {
        throw new AssertionError();
    }

    /**
     * Centers the provided bitmap to a new bitmap with the dimensions of fillSize and fills in any
     * remaining empty space with black pixels.
     */
    public static Bitmap applyFillTransformation(Bitmap bitmap, Point fillSize) {
        // Initialize a new result bitmap with all black pixels.
        Bitmap resultBitmap = Bitmap.createBitmap(fillSize.x, fillSize.y, Config.ARGB_8888);
        resultBitmap.eraseColor(Color.BLACK);

        // Calculate horizontal and vertical offsets between the source and result bitmaps.
        int horizontalOffset = (bitmap.getWidth() - resultBitmap.getWidth()) / 2;
        int verticalOffset = (bitmap.getHeight() - resultBitmap.getHeight()) / 2;

        // Allocate an int array to temporarily store a buffer of the pixel color data we are copying
        // from the source to the final bitmap. We are only copying the portion of the source bitmap
        // that fits within the bounds of the result bitmap, so take the lesser of both bitmap's width
        // and height to calculate the size.
        int pixelArraySize = Math.min(resultBitmap.getWidth(), bitmap.getWidth())
                * Math.min(resultBitmap.getHeight(), bitmap.getHeight());
        int[] srcPixels = new int[pixelArraySize];

        // Copy region of source bitmap into pixel array buffer.
        bitmap.getPixels(
                srcPixels,
                0 /* offset */,
                bitmap.getWidth() /* stride */,
                Math.max(0, horizontalOffset),
                Math.max(0, verticalOffset),
                Math.min(resultBitmap.getWidth(), bitmap.getWidth()) /* width */,
                Math.min(resultBitmap.getHeight(), bitmap.getHeight()) /* height */);

        // Copy the values stored in the pixel array buffer to the result bitmap.
        resultBitmap.setPixels(
                srcPixels,
                0 /* offset */,
                bitmap.getWidth() /* stride */,
                Math.max(0, -1 * horizontalOffset),
                Math.max(0, -1 * verticalOffset),
                Math.min(resultBitmap.getWidth(), bitmap.getWidth()) /* width */,
                Math.min(resultBitmap.getHeight(), bitmap.getHeight()) /* height */);

        return resultBitmap;
    }
}