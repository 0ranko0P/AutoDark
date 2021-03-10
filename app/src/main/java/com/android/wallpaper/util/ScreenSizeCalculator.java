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
package com.android.wallpaper.util;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.view.Display;
import android.view.WindowManager;

import timber.log.Timber;

/**
 * Calculates the size of the device's screen.
 */
public final class ScreenSizeCalculator {

    private static ScreenSizeCalculator sInstance;

    private Point mPortraitScreenSize;
    private Point mLandscapeScreenSize;

    public static ScreenSizeCalculator getInstance() {
        if (sInstance == null) {
            sInstance = new ScreenSizeCalculator();
        }
        return sInstance;
    }

    /**
     * Clears the static instance of ScreenSizeCalculator. Used in test when display metrics are
     * manipulated between test cases.
     */
    static void clearInstance() {
        sInstance = null;
    }

    /**
     * Calculates the device's screen size, in physical pixels.
     *
     * @return Screen size unadjusted for window decor or compatibility scale factors if API level is
     * 17+, otherwise return adjusted screen size. In both cases, returns size in units of
     * physical pixels.
     */
    public Point getScreenSize(Display display) {
        switch (Resources.getSystem().getConfiguration().orientation) {
            case Configuration.ORIENTATION_PORTRAIT:
                return getPortraitScreenSize(display);
            case Configuration.ORIENTATION_LANDSCAPE:
                return getLandscapeScreenSize(display);
            default:
                Timber.e("Unknown device orientation: %s",
                        Resources.getSystem().getConfiguration().orientation);
                return getPortraitScreenSize(display);
        }
    }

    /**
     * Calculates the device's aspect ratio (height/width).
     *
     * Note: The screen size is getting from {@link #getScreenSize}.
     */
    public float getScreenAspectRatio(Context context) {
        final WindowManager windowManager = context.getSystemService(WindowManager.class);
        final Point screenSize = getScreenSize(windowManager.getDefaultDisplay());
        return (float) screenSize.y / screenSize.x;
    }

    private Point getPortraitScreenSize(Display display) {
        if (mPortraitScreenSize == null) {
            mPortraitScreenSize = new Point();
        }
        writeDisplaySizeToPoint(display, mPortraitScreenSize);
        return mPortraitScreenSize;
    }

    private Point getLandscapeScreenSize(Display display) {
        if (mLandscapeScreenSize == null) {
            mLandscapeScreenSize = new Point();
        }
        writeDisplaySizeToPoint(display, mLandscapeScreenSize);
        return mLandscapeScreenSize;
    }

    /**
     * Writes the screen size of the provided display object to the provided Point object.
     */
    private static void writeDisplaySizeToPoint(Display display, Point outPoint) {
        display.getRealSize(outPoint);
    }
}