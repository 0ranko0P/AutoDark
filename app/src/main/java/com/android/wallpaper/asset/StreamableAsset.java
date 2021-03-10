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
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import timber.log.Timber;

/**
 * Represents Asset types for which bytes can be read directly, allowing for flexible bitmap
 * decoding.
 *
 * [0ranko0P] changes:
 *     0. Make AsyncTasks static.
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
                Timber.w("Unsupported EXIF orientation %s", exifOrientation);
                return 0;
        }
    }

    @Override
    public void decodeRawDimensionsAsync(DimensionsReceiver receiver) {
        DecodeDimensionsAsyncTask task = new DecodeDimensionsAsyncTask(this, receiver);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void decodeBitmapAsync(int targetWidth, int targetHeight, BitmapReceiver receiver) {
        DecodeBitmapAsyncTask task = new DecodeBitmapAsyncTask(this, targetWidth, targetHeight, receiver);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @NonNull
    @Override
    public Bitmap decodeBitmap(int targetWidth, int targetHeight) throws IOException {
        return DecodeBitmapAsyncTask.decode(this,targetWidth,targetHeight);
    }

    @Override
    public void decodeBitmapRegionAsync(Rect rect, int targetWidth, int targetHeight,
                                   BitmapReceiver receiver) {
        DecodeBitmapRegionAsyncTask task =
                new DecodeBitmapRegionAsyncTask(this, rect, targetWidth, targetHeight, receiver);
        task.execute();
    }

    @Override
    @WorkerThread
    public @NonNull Bitmap decodeBitmapRegion(Rect rect, int targetWidth, int targetHeight)
            throws IOException, OutOfMemoryError {
        return DecodeBitmapRegionAsyncTask.decode(this, rect, targetWidth, targetHeight);
    }

    /**
     * Fetches an input stream of bytes for the wallpaper image asset and provides the stream
     * asynchronously back to a {@link StreamReceiver}.
     */
    public void fetchInputStream(final StreamReceiver streamReceiver) {
        FetchInputStreamAsyncTask task = new FetchInputStreamAsyncTask(this, streamReceiver);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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
     * Interface for receiving unmodified input streams of the underlying asset without any
     * downscaling or other decoding options.
     */
    public interface StreamReceiver {

        /**
         * Called with an opened input stream of bytes from the underlying image asset. Clients must
         * close the input stream after it has been read. Returns null if there was an error opening the
         * input stream.
         */
        void onInputStreamOpened(@Nullable InputStream inputStream);
    }

    /**
     * AsyncTask which decodes a Bitmap off the UI thread. Scales the Bitmap for the target width and
     * height if possible.
     */
    protected static class DecodeBitmapAsyncTask extends AsyncTask<Void, Void, Bitmap> {
        protected final StreamableAsset mAsset;
        protected final BitmapReceiver mReceiver;
        protected final int mTargetWidth;
        protected final int mTargetHeight;

        public DecodeBitmapAsyncTask(StreamableAsset asset, int targetWidth, int targetHeight,
                                     BitmapReceiver receiver) {
            mAsset = asset;
            mReceiver = receiver;
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
        protected Bitmap doInBackground(Void... unused) {
            try {
                return decode(mAsset, mTargetWidth, mTargetHeight);
            } catch (Exception e) {
                mReceiver.onError(e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null) mReceiver.onBitmapDecoded(bitmap);
        }
    }

    /**
     * AsyncTask subclass which decodes a bitmap region from the asset off the main UI thread.
     */
    protected static final class DecodeBitmapRegionAsyncTask extends AsyncTask<Void, Void, Bitmap> {
        private final StreamableAsset mAsset;
        private final Rect mCropRect;
        private final BitmapReceiver mReceiver;
        private final int mTargetWidth;
        private final int mTargetHeight;

        public DecodeBitmapRegionAsyncTask(StreamableAsset asset, Rect rect, int targetWidth, int targetHeight,
                                           BitmapReceiver receiver) {
            mAsset = asset;
            mCropRect = rect;
            mReceiver = receiver;
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
        protected Bitmap doInBackground(Void... voids) {
            try {
                return decode(mAsset, mCropRect, mTargetWidth, mTargetHeight);
            } catch (Exception e) {
                mReceiver.onError(e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null) mReceiver.onBitmapDecoded(bitmap);
        }
    }

    /**
     * AsyncTask subclass which decodes the raw dimensions of the asset off the main UI thread. Avoids
     * allocating memory for the fully decoded image.
     */
    private static final class DecodeDimensionsAsyncTask extends AsyncTask<Void, Void, Point> {
        private DimensionsReceiver mReceiver;
        private StreamableAsset asset;

        public DecodeDimensionsAsyncTask(StreamableAsset asset, DimensionsReceiver receiver) {
            this.asset = asset;
            this.mReceiver = receiver;
        }

        @Override
        protected Point doInBackground(Void... unused) {
            try {
                return asset.decodeRawDimensions();
            } catch (IOException e) {
                mReceiver.onError(e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(Point dimensions) {
            if (dimensions != null) {
                mReceiver.onDimensionsDecoded(dimensions);
            }
        }
    }

    private static final class FetchInputStreamAsyncTask extends AsyncTask<Void, Void, InputStream> {
        private StreamableAsset mAsset;
        private StreamReceiver mReceiver;

        private FetchInputStreamAsyncTask(StreamableAsset asset, StreamReceiver receiver) {
            mAsset = asset;
            mReceiver = receiver;
        }

        @Override
        protected InputStream doInBackground(Void... params) {
            return mAsset.openInputStream();
        }

        @Override
        protected void onPostExecute(InputStream inputStream) {
            mReceiver.onInputStreamOpened(inputStream);
        }
    }
}