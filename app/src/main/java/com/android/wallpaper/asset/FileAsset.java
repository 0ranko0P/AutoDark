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

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import timber.log.Timber;

/**
 * Asset referenced by a File.
 *
 * [0ranko0P]: Override decodeBitmap to make cropped bitmap align start.
 */
public final class FileAsset extends StreamableAsset {

    public final File mFile;

    /**
     * @param file File representing the asset.
     */
    public FileAsset(File file) {
        mFile = file;
    }

    @NonNull
    @Override
    public Bitmap decodeBitmap(int mTargetWidth, int mTargetHeight) throws IOException {
        Point rawDimensions = decodeRawDimensions();

        float scale = ((float) mTargetHeight) / rawDimensions.y;
        int measuredWidth = Math.round(mTargetWidth / scale);

        // align start (left-most home screen)
        Rect rect = new Rect(0, 0, measuredWidth, rawDimensions.y);
        Bitmap decoded = DecodeBitmapRegionAsyncTask.decode(this, rect, mTargetWidth, mTargetHeight);
        return Bitmap.createScaledBitmap(decoded,
                Math.round(measuredWidth * scale),
                Math.round(rawDimensions.y * scale),
                true);
    }

    @Override
    public void decodeBitmapAsync(int targetWidth, int targetHeight, BitmapReceiver receiver) {
        DecodeBitmapTask task = new DecodeBitmapTask(this, targetWidth, targetHeight, receiver);
        task.execute();
    }

    @Override
    protected InputStream openInputStream() {
        try {
            return new FileInputStream(mFile.getAbsolutePath());
        } catch (FileNotFoundException e) {
            Timber.e(e, "Image file not found");
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return ((FileAsset) o).mFile.equals(mFile);
    }

    @Override
    public int hashCode() {
        return mFile.hashCode();
    }

    @Override
    public String toString() {
        return "FileAsset{" + "mFile=" + mFile + '}';
    }

    public static final class DecodeBitmapTask extends DecodeBitmapAsyncTask {

        public DecodeBitmapTask(StreamableAsset asset, int targetWidth, int targetHeight,
                                BitmapReceiver receiver) {
            super(asset, targetWidth, targetHeight, receiver);
        }

        @Override
        protected Bitmap doInBackground(Void... unused) {
            try {
                return mAsset.decodeBitmap(mTargetWidth, mTargetHeight);
            } catch (IOException e) {
                mReceiver.onError(e);
                return null;
            }
        }
    }
}