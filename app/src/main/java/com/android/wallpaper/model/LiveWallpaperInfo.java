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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Parcel;
import android.service.wallpaper.WallpaperService;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.LiveWallpaperThumbAsset;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import timber.log.Timber;

/**
 * Represents a live wallpaper from the system.
 */
public class LiveWallpaperInfo extends WallpaperInfo {

    public static final Creator<LiveWallpaperInfo> CREATOR =
            new Creator<LiveWallpaperInfo>() {
                @Override
                public LiveWallpaperInfo createFromParcel(Parcel in) {
                    return new LiveWallpaperInfo(in);
                }

                @Override
                public LiveWallpaperInfo[] newArray(int size) {
                    return new LiveWallpaperInfo[size];
                }
            };

    private final android.app.WallpaperInfo mInfo;
    private final ComponentName mComponentName;
    private LiveWallpaperThumbAsset mThumbAsset;

    /**
     * Constructs a LiveWallpaperInfo wrapping the given system WallpaperInfo object, representing
     * a particular live wallpaper.
     *
     * @param info
     */
    public LiveWallpaperInfo(android.app.WallpaperInfo info) {
        this.mInfo = info;
        mComponentName = info.getComponent();
    }

    LiveWallpaperInfo(Parcel in) {
        mInfo = in.readParcelable(android.app.WallpaperInfo.class.getClassLoader());
        mComponentName = mInfo.getComponent();
    }

    public String getTitle(Context context) {
        CharSequence labelCharSeq = mInfo.loadLabel(context.getPackageManager());
        return labelCharSeq == null ? null : labelCharSeq.toString();
    }

    @Override
    @Nullable
    public Asset getAsset(Context context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Asset getThumbAsset(Context context) {
        if (mThumbAsset == null) {
            mThumbAsset = new LiveWallpaperThumbAsset(context, mInfo);
        }
        return mThumbAsset;
    }

    /**
     * Returns all live wallpapers found on the device, excluding those residing in APKs described by
     * the package names in excludedPackageNames.
     */
    public static List<LiveWallpaperInfo> getAll(Context context, @Nullable Set<String> excludedPackageNames) {
        List<ResolveInfo> resolveInfos = getAllOnDevice(context);
        List<LiveWallpaperInfo> wallpaperInfos = new ArrayList<>();
        for (int i = 0; i < resolveInfos.size(); i++) {
            ResolveInfo resolveInfo = resolveInfos.get(i);
            android.app.WallpaperInfo wallpaperInfo;
            try {
                wallpaperInfo = new android.app.WallpaperInfo(context, resolveInfo);
            } catch (XmlPullParserException | IOException e) {
                Timber.w(e, "Skipping wallpaper %s", resolveInfo.serviceInfo);
                continue;
            }

            if (excludedPackageNames != null && excludedPackageNames.contains(
                    wallpaperInfo.getPackageName())) {
                continue;
            }

            wallpaperInfos.add(new LiveWallpaperInfo(wallpaperInfo));
        }

        return wallpaperInfos;
    }

    /**
     * Returns ResolveInfo objects for all live wallpaper services installed on the device. System
     * wallpapers are listed first, unsorted, with other installed wallpapers following sorted
     * in alphabetical order.
     */
    private static List<ResolveInfo> getAllOnDevice(Context context) {
        final PackageManager pm = context.getPackageManager();
        final String packageName = context.getPackageName();

        List<ResolveInfo> resolveInfos = pm.queryIntentServices(
                new Intent(WallpaperService.SERVICE_INTERFACE),
                PackageManager.GET_META_DATA);

        List<ResolveInfo> wallpaperInfos = new ArrayList<>();

        // Remove the "Rotating Image Wallpaper" live wallpaper, which is owned by this package,
        // and separate system wallpapers to sort only non-system ones.
        Iterator<ResolveInfo> iter = resolveInfos.iterator();
        while (iter.hasNext()) {
            ResolveInfo resolveInfo = iter.next();
            if (packageName.equals(resolveInfo.serviceInfo.packageName)) {
                iter.remove();
            } else if (isSystemApp(resolveInfo.serviceInfo.applicationInfo)) {
                wallpaperInfos.add(resolveInfo);
                iter.remove();
            }
        }

        if (resolveInfos.isEmpty()) {
            return wallpaperInfos;
        }

        // Sort non-system wallpapers alphabetically and append them to system ones
        Collections.sort(resolveInfos, new Comparator<ResolveInfo>() {
            final Collator mCollator = Collator.getInstance();

            @Override
            public int compare(ResolveInfo info1, ResolveInfo info2) {
                return mCollator.compare(info1.loadLabel(pm), info2.loadLabel(pm));
            }
        });
        wallpaperInfos.addAll(resolveInfos);

        return wallpaperInfos;
    }

    private static boolean isSystemApp(ApplicationInfo appInfo) {
        return (appInfo.flags & (ApplicationInfo.FLAG_SYSTEM
                | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeParcelable(mInfo, 0 /* flags */);
    }

    public android.app.WallpaperInfo getWallpaperComponent() {
        return mInfo;
    }

    public ComponentName getWallpaperComponentName() {
        return mComponentName;
    }

    public @NotNull String getWallpaperId() {
        return mComponentName.getClassName();
    }

    public String toJsonString() throws JSONException {
        return new JSONObject()
                .put("pkg", mComponentName.getPackageName())
                .put("cls", mComponentName.getClassName())
                .toString();
    }

    public static ComponentName fromJson(String json) throws JSONException {
        JSONObject jsonObj = new JSONObject(json);
        return new ComponentName(jsonObj.getString("pkg"), jsonObj.getString("cls"));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return mComponentName.equals(((LiveWallpaperInfo) o).mComponentName);
    }

    @Override
    public int hashCode() {
        return mComponentName.hashCode();
    }

    @Override
    @NonNull
    public String toString() {
        return "LiveWallpaperInfo{" +
                "mComponentName=" + mComponentName.flattenToString() +
                '}';
    }
}
