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
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.ExifInterface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.wallpaper.util.TaskRunner;
import com.android.wallpaper.util.TaskRunner.Callback;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.Callable;

import timber.log.Timber;

/**
 * Represents Asset types for which bytes can be read directly, allowing for flexible bitmap
 * decoding.
 *
 * [0ranko0P] changes:
 *     0. Replace AsyncTask with Callable.
 *     1. Use Timber instead of Log.
 *     2. Drop some function.
 *     3. Add recycle function since some asset only decode once.
 */
public abstract class StreamableAsset extends Asset {

    @Nullable
    private BitmapRegionDecoder mBitmapRegionDecoder;

    @Nullable
    protected Point mDimensions;

    /**
     * Maps from EXIF orientation tag values to counterclockwise degree rotation values.
     */
    private static int getDegreesRotationForExifOrientation(int exifOrientation) {
        switch (exifOrientation) {
            case ExifInterface.ORIENTATION_NORMAL:
                return 0;
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                Timber.d("Unsupported EXIF orientation %s", exifOrientation);
                return 0;
        }
    }

    @Override
    public void decodeRawDimensionsAsync(DimensionsReceiver receiver) {
        DecodeDimensionsAsyncTask task = new DecodeDimensionsAsyncTask(this);
        TaskRunner.getINSTANCE().executeIOAsync(task, new Callback<Point>() {
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
        DecodeBitmapAsyncTask task = new DecodeBitmapAsyncTask(this, targetWidth, targetHeight);
        TaskRunner.getINSTANCE().executeIOAsync(task, receiver);
    }

    @NonNull
    @Override
    public Bitmap decodeBitmap(int targetWidth, int targetHeight) throws IOException {
        return DecodeBitmapAsyncTask.decode(this,targetWidth,targetHeight);
    }

    @Override
    public void decodeBitmapRegionAsync(Rect rect, int targetWidth, int targetHeight,
                                   Callback<Bitmap> receiver) {
        DecodeBitmapRegionAsyncTask task = new DecodeBitmapRegionAsyncTask(this, rect, targetWidth, targetHeight);
        TaskRunner.getINSTANCE().executeIOAsync(task, receiver);
    }

    @Override
    @WorkerThread
    public @NonNull Bitmap decodeBitmapRegion(Rect rect, int targetWidth, int targetHeight)
            throws IOException, OutOfMemoryError {
        return DecodeBitmapRegionAsyncTask.decode(this, rect, targetWidth, targetHeight);
    }

    /**
     * Fetches an input stream of bytes for the wallpaper image asset and provides the stream
     * asynchronously back to a {@link Callback}.
     */
    public void fetchInputStream(final Callback<InputStream> streamReceiver) {
        TaskRunner.getINSTANCE().executeIOAsync(new FetchInputStreamAsyncTask(this), streamReceiver);
    }

    /**
     * Returns an InputStream representing the asset. Should only be called off the main UI thread.
     */
    @Nullable
    protected abstract InputStream openInputStream();

    /**
     * Gets the EXIF orientation value of the asset. This method should only be called off the main UI
     * thread.
     */
    protected int getExifOrientation() {
        // By default, assume that the EXIF orientation is normal (i.e., bitmap is rotated 0 degrees
        // from how it should be rendered to a viewer).
        return ExifInterface.ORIENTATION_NORMAL;
    }

    /**
     * Decodes the raw dimensions of the asset without allocating memory for the entire asset. Adjusts
     * for the EXIF orientation if necessary.
     *
     * @return Dimensions as a Point where width is represented by "x" and height by "y".
     */
    public @NonNull Point decodeRawDimensions() throws IOException {
        if (mDimensions != null) {
            return mDimensions;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        InputStream inputStream = openInputStream();
        // Input stream may be null if there was an error opening it.
        if (inputStream == null) {
            throw new IOException("error decoding the asset's raw dimensions.");
        }
        BitmapFactory.decodeStream(inputStream, null, options);
        closeInputStream(inputStream, "There was an error closing the input stream used to calculate "
                + "the image's raw dimensions");

        if (options.outWidth == -1 || options.outHeight == -1) {
            throw new IOException("there is an error trying to decode dimensions.");
        }
        int exifOrientation = getExifOrientation();
        // Swap height and width if image is rotated 90 or 270 degrees.
        if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90
                || exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
            mDimensions = new Point(options.outHeight, options.outWidth);
        } else {
            mDimensions = new Point(options.outWidth, options.outHeight);
        }

        return mDimensions;
    }

    /**
     * Returns a BitmapRegionDecoder for the asset.
     */
    private @Nullable BitmapRegionDecoder openBitmapRegionDecoder() {
        InputStream inputStream = null;
        BitmapRegionDecoder brd = null;

        try {
            inputStream = openInputStream();
            // Input stream may be null if there was an error opening it.
            if (inputStream == null) {
                return null;
            }
            brd = BitmapRegionDecoder.newInstance(inputStream, true);
        } catch (IOException e) {
            Timber.w(e, "Unable to open BitmapRegionDecoder");
        } finally {
            if (inputStream != null) {
                closeInputStream(inputStream, "Unable to close input stream used to create "
                        + "BitmapRegionDecoder");
            }
        }

        return brd;
    }

    /**
     * Closes the provided InputStream and if there was an error, logs the provided error message.
     */
    private static void closeInputStream(InputStream inputStream, String errorMessage) {
        try {
            inputStream.close();
        } catch (IOException e) {
            Timber.w(e, errorMessage);
        }
    }

    public void recycle() {
        BitmapRegionDecoder decoder = mBitmapRegionDecoder;
        if (decoder != null && !decoder.isRecycled()) {
            mBitmapRegionDecoder = null;
            decoder.recycle();
        }
    }

    /**
     * CallableTask which decodes a Bitmap off the UI thread. Scales the Bitmap for the target width and
     * height if possible.
     */
    protected static class DecodeBitmapAsyncTask implements Callable<Bitmap> {
        protected final StreamableAsset mAsset;
        protected final int mTargetWidth;
        protected final int mTargetHeight;

        public DecodeBitmapAsyncTask(StreamableAsset asset, int targetWidth, int targetHeight) {
            mAsset = asset;
            mTargetWidth = targetWidth;
            mTargetHeight = targetHeight;
        }

        @WorkerThread
        protected static Bitmap decode(StreamableAsset mAsset, int mTargetWidth, int mTargetHeight) throws IOException {
            int exifOrientation = mAsset.getExifOrientation();
            // Switch target height and width if image is rotated 90 or 270 degrees.
            if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90
                    || exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
                int tempHeight = mTargetHeight;
                mTargetHeight = mTargetWidth;
                mTargetWidth = tempHeight;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();

            Point rawDimensions = mAsset.decodeRawDimensions();

            options.inSampleSize = BitmapUtils.calculateInSampleSize(
                    rawDimensions.x, rawDimensions.y, mTargetWidth, mTargetHeight);
            options.inPreferredConfig = Config.HARDWARE;

            InputStream inputStream = mAsset.openInputStream();
            if (inputStream == null) {
                throw new IOException("Failed to open inputStream.");
            }
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);

            closeInputStream(
                    inputStream, "Error closing the input stream used to decode the full bitmap");
            if (bitmap == null) throw new IOException("Failed to decode bitmap");

            // Rotate output bitmap if necessary because of EXIF orientation tag.
            int matrixRotation = getDegreesRotationForExifOrientation(exifOrientation);
            if (matrixRotation > 0) {
                Matrix rotateMatrix = new Matrix();
                rotateMatrix.setRotate(matrixRotation);
                bitmap = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), rotateMatrix, false);
            }
            return Objects.requireNonNull(bitmap);
        }

        @Override
        public Bitmap call() throws Exception {
            return decode(mAsset, mTargetWidth, mTargetHeight);
        }
    }

    /**
     * Callable subclass which decodes a bitmap region from the asset off the main UI thread.
     */
    protected static final class DecodeBitmapRegionAsyncTask implements Callable<Bitmap> {
        private final StreamableAsset mAsset;
        private final Rect mCropRect;
        private final int mTargetWidth;
        private final int mTargetHeight;

        public DecodeBitmapRegionAsyncTask(StreamableAsset asset, Rect rect, int targetWidth, int targetHeight) {
            mAsset = asset;
            mCropRect = rect;
            mTargetWidth = targetWidth;
            mTargetHeight = targetHeight;
        }

        @WorkerThread
        protected static Bitmap decode(StreamableAsset mAsset, Rect mCropRect, int mTargetWidth,
                                       int mTargetHeight) throws OutOfMemoryError, IOException {
            int exifOrientation = mAsset.getExifOrientation();
            // Switch target height and width if image is rotated 90 or 270 degrees.
            if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90
                    || exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
                int tempHeight = mTargetHeight;
                mTargetHeight = mTargetWidth;
                mTargetWidth = tempHeight;
            }

            // Rotate crop rect if image is rotated more than 0 degrees.
            mCropRect = CropRectRotator.rotateCropRectForExifOrientation(
                    mAsset.decodeRawDimensions(), mCropRect, exifOrientation);

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = BitmapUtils.calculateInSampleSize(
                    mCropRect.width(), mCropRect.height(), mTargetWidth, mTargetHeight);

            if (mAsset.mBitmapRegionDecoder == null) {
                mAsset.mBitmapRegionDecoder = mAsset.openBitmapRegionDecoder();
            }

            // Bitmap region decoder may have failed to open if there was a problem with the underlying
            // InputStream.
            if (mAsset.mBitmapRegionDecoder != null) {
                Bitmap bitmap = mAsset.mBitmapRegionDecoder.decodeRegion(mCropRect, options);
                // Rotate output bitmap if necessary because of EXIF orientation.
                int matrixRotation = getDegreesRotationForExifOrientation(exifOrientation);
                if (matrixRotation > 0) {
                    Matrix rotateMatrix = new Matrix();
                    rotateMatrix.setRotate(matrixRotation);
                    bitmap = Bitmap.createBitmap(
                            bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), rotateMatrix, false);
                }
                return bitmap;
            } else {
                throw new IOException("Unable to open BitmapRegionDecoder");
            }
        }

        @Override
        public Bitmap call() throws IOException {
            return decode(mAsset, mCropRect, mTargetWidth, mTargetHeight);
        }
    }

    /**
     * Callable subclass which decodes the raw dimensions of the asset off the main UI thread. Avoids
     * allocating memory for the fully decoded image.
     */
    private static final class DecodeDimensionsAsyncTask implements Callable<Point> {
        private final StreamableAsset asset;

        public DecodeDimensionsAsyncTask(StreamableAsset asset) {
            this.asset = asset;
        }

        @Override
        public Point call() throws Exception {
            return asset.decodeRawDimensions();
        }
    }

    private static final class FetchInputStreamAsyncTask implements Callable<InputStream> {
        private final StreamableAsset mAsset;

        private FetchInputStreamAsyncTask(StreamableAsset asset) {
            mAsset = asset;
        }

        @Override
        public InputStream call() {
            return Objects.requireNonNull(mAsset.openInputStream());
        }
    }
}