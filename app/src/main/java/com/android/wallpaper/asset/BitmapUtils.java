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

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;

/**
 * Collection of static utility methods for decoding and processing Bitmaps.
 */
public final class BitmapUtils {
    private static final float DEFAULT_CENTER_ALIGNMENT = 0.5f;

    // Suppress default constructor for noninstantiability.
    private BitmapUtils() {
        throw new AssertionError();
    }

    /**
     * Calculates the highest subsampling factor to scale the source image to the target view without
     * losing visible quality. Final result is based on powers of 2 because it should be set as
     * BitmapOptions#inSampleSize.
     *
     * @param srcWidth     Width of source image.
     * @param srcHeight    Height of source image.
     * @param targetWidth  Width of target view.
     * @param targetHeight Height of target view.
     * @return Highest subsampling factor as a power of 2.
     */
    public static int calculateInSampleSize(
            int srcWidth, int srcHeight, int targetWidth, int targetHeight) {
        int shift = 0;
        int halfHeight = srcHeight / 2;
        int halfWidth = srcWidth / 2;

        // Calculate the largest inSampleSize value that is a power of 2 and keeps both the result
        // bitmap's height and width at least as large as the target height and width.
        while (((halfHeight >> shift) >= targetHeight) && ((halfWidth >> shift) >= targetWidth)) {
            shift++;
        }

        return 1 << shift;
    }

    /**
     * Generates a hash code for the given bitmap. Computation starts with a nonzero prime number,
     * then for the integer values of height, width, and a selection of pixel colors, multiplies the
     * result by 31 and adds said integer value. Multiply by 31 because it is prime and conveniently 1
     * less than 32 which is 2 ^ 5, allowing the VM to replace multiplication by a bit shift and
     * subtraction for performance.
     * <p>
     * This method should be called off the UI thread.
     */
    public static long generateHashCode(Bitmap bitmap) {
        long result = 17;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        result = 31 * result + width;
        result = 31 * result + height;

        // Traverse pixels exponentially so that hash code generation scales well with large images.
        for (int x = 0; x < width; x = x * 2 + 1) {
            for (int y = 0; y < height; y = y * 2 + 1) {
                result = 31 * result + bitmap.getPixel(x, y);
            }
        }

        return result;
    }

    /**
     * Calculates horizontal alignment of the rect within the supplied dimensions.
     *
     * @return A float value between 0 and 1 specifying horizontal alignment; 0 for left-aligned, 0.5
     * for horizontal center-aligned, and 1 for right-aligned.
     */
    public static float calculateHorizontalAlignment(Point dimensions, Rect rect) {
        int paddingLeft = rect.left;
        int paddingRight = dimensions.x - rect.right;
        int totalHorizontalPadding = paddingLeft + paddingRight;
        // Zero horizontal padding means that there is no room to crop horizontally so we just fall
        // back to a default center-alignment value.
        return (totalHorizontalPadding == 0)
                ? DEFAULT_CENTER_ALIGNMENT
                : paddingLeft / ((float) paddingLeft + paddingRight);
    }

    /**
     * Calculates vertical alignment of the rect within the supplied dimensions.
     *
     * @return A float value between 0 and 1 specifying vertical alignment; 0 for top-aligned, 0.5 for
     * vertical center-aligned, and 1 for bottom-aligned.
     */
    public static float calculateVerticalAlignment(Point dimensions, Rect rect) {
        int paddingTop = rect.top;
        int paddingBottom = dimensions.y - rect.bottom;
        int totalVerticalPadding = paddingTop + paddingBottom;
        // Zero vertical padding means that there is no room to crop vertically so we just fall back to
        // a default center-alignment value.
        return (totalVerticalPadding == 0)
                ? DEFAULT_CENTER_ALIGNMENT
                : paddingTop / ((float) paddingTop + paddingBottom);
    }
}