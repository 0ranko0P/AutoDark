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
import android.graphics.Point;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;

import com.android.wallpaper.util.TaskRunner;
import com.android.wallpaper.util.TaskRunner.Callback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;

import timber.log.Timber;

import static android.media.ExifInterface.ORIENTATION_NORMAL;
import static android.media.ExifInterface.ORIENTATION_UNDEFINED;
import static android.media.ExifInterface.TAG_ORIENTATION;

/**
 * Represents an asset located via an Android content URI.
 *
 * [0ranko0P]: Drop ExifInterfaceCompat since AutoDark are running on Q.
 */
@SuppressWarnings("Exifinterface")
public class ContentUriAsset extends StreamableAsset {
    private static final String JPEG_MIME_TYPE = "image/jpeg";
    private static final String PNG_MIME_TYPE = "image/png";

    private final Context mContext;
    public final Uri mUri;

    private ExifInterface mExif;
    private int mExifOrientation;

    /**
     * @param context The application's context.
     * @param uri     Content URI locating the asset.
     */
    public ContentUriAsset(Context context, Uri uri) {
        mExifOrientation = ORIENTATION_UNDEFINED;
        mContext = context.getApplicationContext();
        mUri = uri;
    }

    @Override
    public void decodeBitmapRegionAsync(final Rect rect, int targetWidth, int targetHeight,
                                   final Callback<Bitmap> receiver) {
        // BitmapRegionDecoder only supports images encoded in either JPEG or PNG, so if the content
        // URI asset is encoded with another format (for example, GIF), then fall back to cropping a
        // bitmap region from the full-sized bitmap.
        if (isJpeg() || isPng()) {
            super.decodeBitmapRegionAsync(rect, targetWidth, targetHeight, receiver);
            return;
        }

        decodeRawDimensionsAsync(new DimensionsReceiver() {
            @Override
            public void onDimensionsDecoded(@NonNull Point dimensions) {

                decodeBitmapAsync(dimensions.x, dimensions.y, new Callback<Bitmap>() {
                    @Override
                    public void onComplete(Bitmap fullBitmap) {
                        BitmapCropTask task = new BitmapCropTask(fullBitmap, rect);
                        TaskRunner.getINSTANCE().executeIOAsync(task, receiver);
                    }

                    @Override
                    public void onError(Exception e) {
                        receiver.onError(e);
                    }
                });
            }

            @Override
            public void onError(@Nullable Exception e) {
                receiver.onError(e);
            }
        });
    }

    @Override
    @WorkerThread
    public @NonNull Bitmap decodeBitmapRegion(final Rect rect, int targetWidth, int targetHeight)
            throws IOException, OutOfMemoryError {
        if (isJpeg() || isPng()) {
            return super.decodeBitmapRegion(rect, targetWidth, targetHeight);
        }
        Point dimensions = decodeRawDimensions();
        Bitmap fullBitmap = StreamableAsset.DecodeBitmapAsyncTask.decode(this, dimensions.x, dimensions.y);
        return Bitmap.createBitmap(fullBitmap, rect.left, rect.top, rect.width(), rect.height());
    }

    /**
     * Returns whether this image is encoded in the JPEG file format.
     */
    public boolean isJpeg() {
        String mimeType = mContext.getContentResolver().getType(mUri);
        return mimeType != null && mimeType.equals(JPEG_MIME_TYPE);
    }

    /**
     * Returns whether this image is encoded in the PNG file format.
     */
    public boolean isPng() {
        String mimeType = mContext.getContentResolver().getType(mUri);
        return mimeType != null && mimeType.equals(PNG_MIME_TYPE);
    }

    private void ensureExifInterface() {
        if (mExif == null) {
            try (InputStream inputStream = openInputStream()) {
                if (inputStream != null) {
                    mExif = new ExifInterface(inputStream);
                }
            } catch (IOException e) {
                Timber.w(e, "Couldn't read stream for %s", mUri);
            }
        }
    }

    @Override
    public InputStream openInputStream() {
        try {
            return mContext.getContentResolver().openInputStream(mUri);
        } catch (FileNotFoundException e) {
            Timber.w(e, "Image file not found");
            return null;
        }
    }

    @Override
    protected int getExifOrientation() {
        if (mExifOrientation != ORIENTATION_UNDEFINED) {
            return mExifOrientation;
        }

        mExifOrientation = readExifOrientation();
        return mExifOrientation;
    }

    /**
     * Returns the EXIF rotation for the content URI asset. This method should only be called off
     * the main UI thread.
     */
    private int readExifOrientation() {
        ensureExifInterface();
        if (mExif == null) {
            Timber.w("Unable to read EXIF rotation for content URI asset with content URI: %s", mUri);
            return ORIENTATION_NORMAL;
        }

        return mExif.getAttributeInt(TAG_ORIENTATION, ORIENTATION_NORMAL);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return mUri.equals(((ContentUriAsset) o).mUri);
    }

    @Override
    public int hashCode() {
        return mUri.hashCode();
    }

    /**
     * Custom Callable which crops a bitmap region from a larger bitmap.
     */
    private final static class BitmapCropTask implements Callable<Bitmap> {
        private final Bitmap mFromBitmap;
        private final Rect mCropRect;

        public BitmapCropTask(@NonNull Bitmap fromBitmap, Rect cropRect) {
            mFromBitmap = fromBitmap;
            mCropRect = cropRect;
        }

        @Override
        public Bitmap call(){
            Bitmap bitmap = Bitmap.createBitmap(
                    mFromBitmap, mCropRect.left, mCropRect.top, mCropRect.width(),
                    mCropRect.height());

            mFromBitmap.recycle();
            return bitmap;
        }
    }
}