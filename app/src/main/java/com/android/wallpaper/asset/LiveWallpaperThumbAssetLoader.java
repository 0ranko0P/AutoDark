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

import android.graphics.drawable.Drawable;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.signature.ObjectKey;

/**
 * Glide model loader for live wallpaper thumbnails.
 */
public final class LiveWallpaperThumbAssetLoader implements
        ModelLoader<LiveWallpaperThumbAsset, Drawable> {

    @Override
    public boolean handles(LiveWallpaperThumbAsset liveWallpaperThumbAsset) {
        return true;
    }

    @Override
    public LoadData<Drawable> buildLoadData(LiveWallpaperThumbAsset liveWallpaperThumbAsset,
                                            int unusedWidth, int unusedHeight, Options options) {
        return new LoadData<>(new ObjectKey(liveWallpaperThumbAsset),
                new LiveWallpaperThumbFetcher(liveWallpaperThumbAsset));
    }

    /**
     * Factory that constructs {@link LiveWallpaperThumbAssetLoader} instances.
     */
    public static class LiveWallpaperThumbAssetLoaderFactory
            implements ModelLoaderFactory<LiveWallpaperThumbAsset, Drawable> {
        public LiveWallpaperThumbAssetLoaderFactory() {
        }

        @Override
        public ModelLoader<LiveWallpaperThumbAsset, Drawable> build(
                MultiModelLoaderFactory multiFactory) {
            return new LiveWallpaperThumbAssetLoader();
        }

        @Override
        public void teardown() {
            // no-op
        }
    }

    /**
     * Fetcher class for fetching wallpaper image data from a {@link LiveWallpaperThumbAsset}.
     */
    private static final class LiveWallpaperThumbFetcher implements DataFetcher<Drawable> {

        private LiveWallpaperThumbAsset mLiveWallpaperThumbAsset;

        public LiveWallpaperThumbFetcher(LiveWallpaperThumbAsset liveWallpaperThumbAsset) {
            mLiveWallpaperThumbAsset = liveWallpaperThumbAsset;
        }

        @Override
        public void loadData(Priority priority, DataCallback<? super Drawable> callback) {
            callback.onDataReady(mLiveWallpaperThumbAsset.getThumbnailDrawable());
        }

        @Override
        public DataSource getDataSource() {
            return DataSource.LOCAL;
        }

        @Override
        public void cancel() {
            // no-op
        }

        @Override
        public void cleanup() {
            // no-op
        }

        @Override
        public Class<Drawable> getDataClass() {
            return Drawable.class;
        }
    }
}