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
import android.os.Parcel;
import android.os.Parcelable;

import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.ContentUriAsset;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a wallpaper image from the system's image picker.
 */
public final class ImageWallpaperInfo extends WallpaperInfo {

    public static final Parcelable.Creator<ImageWallpaperInfo> CREATOR =
            new Parcelable.Creator<ImageWallpaperInfo>() {
                @Override
                public ImageWallpaperInfo createFromParcel(Parcel in) {
                    return new ImageWallpaperInfo(in);
                }

                @Override
                public ImageWallpaperInfo[] newArray(int size) {
                    return new ImageWallpaperInfo[size];
                }
            };

    private final Uri mUri;
    private ContentUriAsset mAsset;

    public ImageWallpaperInfo(Uri uri) {
        mUri = uri;
    }

    protected ImageWallpaperInfo(Parcel in) {
        mUri = Uri.parse(in.readString());
    }

    @Override
    public Asset getAsset(Context context) {
        if (mAsset == null) {
            mAsset = new ContentUriAsset(context, mUri);
        }
        return mAsset;
    }

    @Override
    public Asset getThumbAsset(Context context) {
        return getAsset(context);
    }

    public Uri getUri() {
        return mUri;
    }

    @Override
    public @NotNull String getWallpaperId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(mUri.toString());
    }
}
