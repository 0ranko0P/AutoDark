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
package com.android.wallpaper.model;

import android.content.Context;
import android.net.Uri;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.android.wallpaper.asset.Asset;

/**
 * Interface for wallpaper info model.
 */
public abstract class WallpaperInfo implements Parcelable {

    /**
     * @param context
     * @return The title for this wallpaper, if applicable (as in a wallpaper "app" or live
     * wallpaper), or null if not applicable.
     */
    public String getTitle(Context context) {
        return null;
    }

    /** Returns the URI corresponding to the wallpaper, or null if none exists. */
    public Uri getUri() {
        return null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * @param context The client application's context.
     * @return The {@link Asset} representing the wallpaper image.
     */
    public abstract Asset getAsset(Context context);

    /**
     * @param context The client application's context.
     * @return The {@link Asset} representing the wallpaper's thumbnail.
     */
    public abstract Asset getThumbAsset(Context context);

    /**
     * @return the {@link android.app.WallpaperInfo} associated with this wallpaper, which is
     * generally present for live wallpapers, or null if there is none.
     */
    public android.app.WallpaperInfo getWallpaperComponent() {
        return null;
    }

    /**
     * Returns the ID of this wallpaper.
     */
    @NonNull
    public abstract String getWallpaperId();
}