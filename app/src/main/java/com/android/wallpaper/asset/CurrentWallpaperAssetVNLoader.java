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

import android.os.ParcelFileDescriptor.AutoCloseInputStream;

import androidx.annotation.Nullable;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.signature.ObjectKey;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

/**
 * Glide custom model loader for {@link CurrentWallpaperAssetVN}.
 */
public final class CurrentWallpaperAssetVNLoader implements ModelLoader<CurrentWallpaperAssetVN, InputStream> {

    @Override
    public boolean handles(@NotNull CurrentWallpaperAssetVN asset) {
        return true;
    }

    @Override
    public LoadData<InputStream> buildLoadData(@NotNull CurrentWallpaperAssetVN currentWallpaperAssetVN,
                                               int width, int height, @NotNull Options options) {
        return new LoadData<>(new ObjectKey(currentWallpaperAssetVN),
                new CurrentWallpaperAssetVNDataFetcher(currentWallpaperAssetVN));
    }

    public static final class CurrentWallpaperAssetVNLoaderFactory
            implements ModelLoaderFactory<CurrentWallpaperAssetVN, InputStream> {

        @Override
        public @NotNull ModelLoader<CurrentWallpaperAssetVN, InputStream> build(
                @NotNull MultiModelLoaderFactory multiFactory) {
            return new CurrentWallpaperAssetVNLoader();
        }

        @Override
        public void teardown() {
            // no-op
        }
    }

    private static final class CurrentWallpaperAssetVNDataFetcher implements DataFetcher<InputStream> {

        private final CurrentWallpaperAssetVN mAsset;

        @Nullable
        private InputStream ins;

        public CurrentWallpaperAssetVNDataFetcher(CurrentWallpaperAssetVN asset) {
            mAsset = asset;
        }

        @Override
        public void loadData(@NotNull Priority priority, @NotNull final DataCallback<? super InputStream> callback) {
            try {
                ins = new AutoCloseInputStream(mAsset.getWallpaperPfd());
                callback.onDataReady(ins);
            } catch (Exception e) {
                callback.onLoadFailed(e);
            }
        }

        @Override
        public @NotNull DataSource getDataSource() {
            return DataSource.LOCAL;
        }

        @Override
        public void cancel() {
            // no op
        }

        @Override
        public void cleanup() {
            if (ins != null) {
                try {
                    ins.close();
                } catch (IOException ignored) {
                }
            }
        }

        @Override
        public @NotNull Class<InputStream> getDataClass() {
            return InputStream.class;
        }
    }
}