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

import android.annotation.SuppressLint;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.BitmapUtils;
import com.android.wallpaper.asset.StreamableAsset;
import com.android.wallpaper.module.BitmapCropper.Callback;
import com.android.wallpaper.util.BitmapTransformer;
import com.android.wallpaper.util.TaskRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;

import timber.log.Timber;

/**
 * Interface for classes which persist wallpapers to the system.
 *
 * [0ranko0P] changes:
 *     0. setIndividualWallpaper to saveCroppedWallpaper.
 *     1. Compress to lossy WEBP by default.
 *     2. Drop WallpaperChangedNotifier
 *     3. Replace AsyncTask with Callable.
 */
@SuppressLint({"MissingPermission"})
public final class WallpaperPersister {

    public static final int DEFAULT_COMPRESS_QUALITY = 90;
    public static final Bitmap.CompressFormat DEFAULT_COMPRESS_FORMAT = Bitmap.CompressFormat.WEBP;

    public static final int DEST_HOME_SCREEN = 0;
    public static final int DEST_LOCK_SCREEN = 1;
    public static final int DEST_BOTH = 2;

    /**
     * The possible destinations to which a wallpaper may be set.
     */
    @IntDef({
            DEST_HOME_SCREEN,
            DEST_LOCK_SCREEN,
            DEST_BOTH})
    public @interface Destination {
    }

    /**
     * Interface for tracking success or failure of set wallpaper operations.
     */
    public interface SetWallpaperCallback extends Asset.ErrorReceiver{
        void onSuccess(String id);
    }

    // Context that accesses files in device protected storage
    public final WallpaperManager mWallpaperManager;

    @SuppressLint("ServiceCast")
    public WallpaperPersister(Context context) {
        // Retrieve WallpaperManager using Context#getSystemService instead of
        mWallpaperManager = (WallpaperManager) context.getSystemService(Context.WALLPAPER_SERVICE);
    }

    public void saveCroppedWallpaper(@NonNull Asset asset, float wallpaperScale,
                                     @NonNull Rect cropRect, @NonNull File parent,
                                     @NonNull SetWallpaperCallback callback) {
        BitmapCropper.cropAndScaleBitmapAsync(asset, wallpaperScale, cropRect, new Callback() {
            @Override
            public void onBitmapCropped(@NonNull Bitmap croppedBitmap) {
                PersistWallpaperTask task = new PersistWallpaperTask(parent, croppedBitmap);
                TaskRunner.getINSTANCE().executeIOAsync(task, new TaskRunner.Callback<String>() {
                    @Override
                    public void onComplete(String id) {
                        callback.onSuccess(id);
                    }

                    @Override
                    public void onError(Exception e) {
                        callback.onError(e);
                    }
                });
            }

            @Override
            public void onError(@Nullable Exception e) {
                callback.onError(e);
            }
        });
    }

    public void setIndividualWallpaper(StreamableAsset asset, @Destination final int destination,
                                       @Nullable SetWallpaperCallback callback) {
        asset.fetchInputStream(new TaskRunner.Callback<InputStream>() {
            @Override
            public void onComplete(InputStream inputStream) {
                setIndividualWallpaper(inputStream, destination, callback);
            }

            @Override
            public void onError(Exception e) {
                if (callback != null) {
                    callback.onError(e);
                } else {
                    Timber.e(e, "Failed to obtain inputStream.");
                }
            }
        });
    }

    /**
     * Sets a static individual wallpaper stream to the system via the WallpaperManager.
     *
     * @param inputStream JPEG or PNG stream of wallpaper image's bytes.
     * @param destination The destination - where to set the wallpaper to.
     * @param callback    Called once the wallpaper was set or if an error occurred.
     */
    private void setIndividualWallpaper(InputStream inputStream,
                                        @Destination int destination,
                                        @Nullable SetWallpaperCallback callback) {
        SetWallpaperTask setWallpaperTask = new SetWallpaperTask(inputStream, destination, mWallpaperManager);
        TaskRunner.getINSTANCE().executeAsync(setWallpaperTask, new TaskRunner.Callback<Integer>() {
            @Override
            public void onComplete(Integer wallpaperId) {
                if (callback == null) return;

                if (wallpaperId > 0) {
                    callback.onSuccess(String.valueOf(wallpaperId));
                } else {
                    callback.onError(null);
                }
            }

            @Override
            public void onError(Exception e) {
                if (callback == null) {
                    Timber.e(e, "Unable to set wallpaper");
                } else {
                    callback.onError(e);
                }
            }
        });
    }

    /**
     * Sets a wallpaper bitmap to the {@link WallpaperManager}.
     *
     * @return an integer wallpaper ID. This is an actual wallpaper ID on N and later versions of
     * Android, otherwise on pre-N versions of Android will return a positive integer when the
     * operation was successful and zero if the operation encountered an error.
     */
    private static int setBitmapToWallpaperManager(Bitmap wallpaperBitmap, boolean allowBackup,
                                                   int whichWallpaper, WallpaperManager mWallpaperManager) {
        ByteArrayOutputStream tmpOut = new ByteArrayOutputStream();
        if (wallpaperBitmap.compress(DEFAULT_COMPRESS_FORMAT, DEFAULT_COMPRESS_QUALITY, tmpOut)) {
            try {
                byte[] outByteArray = tmpOut.toByteArray();
                return mWallpaperManager.setStream(
                        new ByteArrayInputStream(outByteArray),
                        null /* visibleCropHint */,
                        allowBackup,
                        whichWallpaper);
            } catch (IOException e) {
                Timber.e(e, "unable to write stream to wallpaper manager");
                return 0;
            }
        } else {
            Timber.e( "unable to compress wallpaper");
            try {
                return mWallpaperManager.setBitmap(
                        wallpaperBitmap,
                        null /* visibleCropHint */,
                        allowBackup,
                        whichWallpaper);
            } catch (IOException e) {
                Timber.e(e, "unable to set wallpaper");
                return 0;
            }
        }
    }

    private static final class PersistWallpaperTask implements Callable<String> {
        private final File mParent;
        private Bitmap bitmap;

        PersistWallpaperTask(File parent, Bitmap bitmap) {
            this.mParent = parent;
            this.bitmap = bitmap;
        }

        @Override
        public String call() throws IOException {
            if (!mParent.exists()) {
                if (!mParent.mkdirs()) {
                    throw new IOException("Unable to create " + mParent);
                }
            }
            String id = String.valueOf(BitmapUtils.generateHashCode(bitmap));
            File target = new File(mParent, id);
            try (FileOutputStream out = new FileOutputStream(target)) {
                if (bitmap.compress(DEFAULT_COMPRESS_FORMAT, DEFAULT_COMPRESS_QUALITY, out)) {
                    return id;
                } else {
                    throw new IOException("Failed to compress bitmap, path: " + target);
                }
            } finally {
                bitmap.recycle();
                bitmap = null;
            }
        }
    }

    public static final class SetWallpaperTask implements Callable<Integer> {
        @Destination
        private final int mDestination;

        private Bitmap mBitmap;
        private InputStream mInputStream;

        private final WallpaperManager mWallpaperManager;

        private boolean allowBackup;

        /**
         * Optional parameters for applying a post-decoding fill or stretch transformation.
         */
        @Nullable
        private Point mFillSize;
        @Nullable
        private Point mStretchSize;

        SetWallpaperTask(@NonNull Bitmap bitmap, @Destination int destination, WallpaperManager wallpaperManager) {
            super();
            mBitmap = bitmap;
            mDestination = destination;
            mWallpaperManager = wallpaperManager;
        }

        /**
         * Constructor for SetWallpaperTask which takes an InputStream instead of a bitmap. The task
         * will close the InputStream once it is done with it.
         */
        public SetWallpaperTask(@NonNull InputStream stream, @Destination int destination, WallpaperManager wallpaperManager) {
            super();
            mInputStream = stream;
            mDestination = destination;
            mWallpaperManager = wallpaperManager;
        }

        void setFillSize(Point fillSize) {
            if (mStretchSize != null) {
                throw new IllegalArgumentException(
                        "Can't pass a fill size option if a stretch size is "
                                + "already set.");
            }
            mFillSize = fillSize;
        }

        void setStretchSize(Point stretchSize) {
            if (mFillSize != null) {
                throw new IllegalArgumentException(
                        "Can't pass a stretch size option if a fill size is "
                                + "already set.");
            }
            mStretchSize = stretchSize;
        }

        @Override
        public Integer call() throws IOException {
            int whichWallpaper;
            if (mDestination == DEST_HOME_SCREEN) {
                whichWallpaper = WallpaperManager.FLAG_SYSTEM;
            } else if (mDestination == DEST_LOCK_SCREEN) {
                whichWallpaper = WallpaperManager.FLAG_LOCK;
            } else { // DEST_BOTH
                whichWallpaper = WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK;
            }

            final int wallpaperId;
            if (mBitmap != null) {
                // Apply fill or stretch transformations on mBitmap if necessary.
                if (mFillSize != null) {
                    mBitmap = BitmapTransformer.applyFillTransformation(mBitmap, mFillSize);
                }
                if (mStretchSize != null) {
                    mBitmap = Bitmap.createScaledBitmap(mBitmap, mStretchSize.x, mStretchSize.y,
                            true);
                }

                wallpaperId = setBitmapToWallpaperManager(mBitmap, allowBackup, whichWallpaper, mWallpaperManager);
            } else if (mInputStream != null) {
                wallpaperId = mWallpaperManager.setStream(mInputStream, null, allowBackup,
                        whichWallpaper);
            } else {
                throw new NullPointerException(
                        "Both the wallpaper bitmap and input stream are null so we're unable to "
                                + "set any "
                                + "kind of wallpaper here.");
            }

            if (mInputStream != null) {
                try {
                    mInputStream.close();
                } catch (IOException e) {
                    Timber.e(e, "Failed to close input stream ");
                }
            }

            return wallpaperId;
        }
    }
}