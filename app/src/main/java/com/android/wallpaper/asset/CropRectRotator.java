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

import android.graphics.Point;
import android.graphics.Rect;
import android.media.ExifInterface;

import timber.log.Timber;

/**
 * Rotates crop rectangles for bitmap region operations on rotated images (i.e., with non-normal
 * EXIF orientation).
 */
public final class CropRectRotator {
    /**
     * Rotates and returns a new crop Rect which is adjusted for the provided EXIF orientation value.
     */
    public static Rect rotateCropRectForExifOrientation(Point dimensions, Rect srcRect,
                                                        int exifOrientation) {

        switch (exifOrientation) {
            case ExifInterface.ORIENTATION_NORMAL:
                return new Rect(srcRect);
            case ExifInterface.ORIENTATION_ROTATE_90:
                return new Rect(srcRect.top, dimensions.x - srcRect.right, srcRect.bottom,
                        dimensions.x - srcRect.left);
            case ExifInterface.ORIENTATION_ROTATE_180:
                return new Rect(dimensions.x - srcRect.right, dimensions.y - srcRect.bottom,
                        dimensions.x - srcRect.left, dimensions.y - srcRect.top);
            case ExifInterface.ORIENTATION_ROTATE_270:
                return new Rect(dimensions.y - srcRect.bottom, srcRect.left, dimensions.y - srcRect.top,
                        srcRect.right);
            default:
                Timber.w("Unsupported EXIF orientation %s", exifOrientation);
                return new Rect(srcRect);
        }
    }
}