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
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;

/**
 * Static utility methods for wallpaper cropping operations.
 */
public final class WallpaperCropUtils {

    private static final float WALLPAPER_SCREENS_SPAN = 2f;
    private static final float ASPECT_RATIO_LANDSCAPE = 16 / 10f;
    private static final float ASPECT_RATIO_PORTRAIT = 10 / 16f;
    private static final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE = 1.2f;
    private static final float WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT = 1.5f;

    /**
     * Calculates the center area of the outer rectangle which is visible in the inner rectangle
     * after applying the minimum zoom.
     *
     * @param outer the size of outer rectangle as a Point (x,y).
     * @param inner the size of inner rectangle as a Point (x,y).
     */
    public static Rect calculateVisibleRect(Point outer, Point inner) {
        PointF visibleRectCenter = new PointF(outer.x / 2f, outer.y / 2f);
        if (inner.x / (float) inner.y > outer.x / (float) outer.y) {
            float minZoom = inner.x / (float) outer.x;
            float visibleRectHeight = inner.y / minZoom;
            return new Rect(0, (int) (visibleRectCenter.y - visibleRectHeight / 2),
                    outer.x, (int) (visibleRectCenter.y + visibleRectHeight / 2));
        } else {
            float minZoom = inner.y / (float) outer.y;
            float visibleRectWidth = inner.x / minZoom;
            return new Rect((int) (visibleRectCenter.x - visibleRectWidth / 2),
                    0, (int) (visibleRectCenter.x + visibleRectWidth / 2), outer.y);
        }
    }

    /**
     * Calculates the center position of a wallpaper of the given size, based on a "crop surface"
     * (with extra width to account for parallax) superimposed on the screen. Trying to show as
     * much of the wallpaper as possible on the crop surface and align screen to crop surface such
     * that the centered wallpaper matches what would be seen by the user in the left-most home
     * screen.
     *
     * @param wallpaperSize full size of the wallpaper
     * @param visibleWallpaperRect visible area available for the wallpaper
     * @return a point corresponding to the position of wallpaper that should be in the center
     *      of the screen.
     */
    public static PointF calculateDefaultCenter(Context context, Point wallpaperSize,
                                                Rect visibleWallpaperRect) {

        WallpaperCropUtils.adjustCurrentWallpaperCropRect(context, wallpaperSize,
                visibleWallpaperRect);

        return new PointF(visibleWallpaperRect.centerX(),
                visibleWallpaperRect.centerY());
    }

    public static void adjustCurrentWallpaperCropRect(Context context, Point assetDimensions,
                                                      Rect cropRect) {
        adjustCropRect(context, cropRect, true /* zoomIn */);
    }

    /** Adjust the crop rect by zooming in with systemWallpaperMaxScale. */
    public static void adjustCropRect(Context context, Rect cropRect, boolean zoomIn) {
        float centerX = cropRect.centerX();
        float centerY = cropRect.centerY();
        float width = cropRect.width();
        float height = cropRect.height();
        float systemWallpaperMaxScale = getSystemWallpaperMaximumScale(context);
        float scale = zoomIn ? systemWallpaperMaxScale : 1.0f / systemWallpaperMaxScale;

        // Adjust the rect according to the system wallpaper's maximum scale.
        int left = (int) (centerX - (width / 2) / scale);
        int top = (int) (centerY - (height / 2) / scale);
        int right = (int) (centerX + (width / 2) / scale);
        int bottom = (int) (centerY + (height / 2) / scale);
        cropRect.set(left, top, right, bottom);
        if (left < 0) {
            cropRect.offsetTo(0, top);
        }
    }

    /**
     * Calculates the minimum zoom such that the maximum surface area of the outer rectangle is
     * visible within the inner rectangle.
     *
     * @param outer Size of outer rectangle as a Point (x,y).
     * @param inner Size of inner rectangle as a Point (x,y).
     */
    public static float calculateMinZoom(Point outer, Point inner) {
        float minZoom;
        if (inner.x / (float) inner.y > outer.x / (float) outer.y) {
            minZoom = inner.x / (float) outer.x;
        } else {
            minZoom = inner.y / (float) outer.y;
        }
        return minZoom;
    }

    /**
     * Calculates parallax travel (i.e., extra width) for a screen with the given resolution.
     */
    public static float wallpaperTravelToScreenWidthRatio(int width, int height) {
        float aspectRatio = width / (float) height;

        // At an aspect ratio of 16/10, the wallpaper parallax effect should span 1.2 * screen width
        // At an aspect ratio of 10/16, the wallpaper parallax effect should span 1.5 * screen width
        // We will use these two data points to extrapolate how much the wallpaper parallax effect
        // to span (i.e., travel) at any aspect ratio. We use the following two linear formulas,
        // where
        // the coefficient on x is the aspect ratio (width/height):
        //   (16/10)x + y = 1.2
        //   (10/16)x + y = 1.5
        // We solve for x and y and end up with a final formula:
        float x = (WALLPAPER_WIDTH_TO_SCREEN_RATIO_LANDSCAPE
                - WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT)
                / (ASPECT_RATIO_LANDSCAPE - ASPECT_RATIO_PORTRAIT);
        float y = WALLPAPER_WIDTH_TO_SCREEN_RATIO_PORTRAIT - x * ASPECT_RATIO_PORTRAIT;
        return x * aspectRatio + y;
    }

    /** Adjust the given Point, representing a size by  systemWallpaperMaxScale. */
    public static void scaleSize(Context context, Point size) {
        float systemWallpaperMaxScale = getSystemWallpaperMaximumScale(context);
        size.set((int) (size.x * systemWallpaperMaxScale),
                (int) (size.y * systemWallpaperMaxScale));
    }

    /**
     * Calculates {@link Rect} of the wallpaper which we want to crop to in physical pixel terms
     * (i.e., scaled to current zoom).
     *
     * @param hostViewSize            the size of the view hosting the wallpaper as a Point (x,y).
     * @param cropSize                the default size of the crop as a Point (x,y).
     * @param rawWallpaperSize        the size of the raw wallpaper as a Point (x,y).
     * @param visibleRawWallpaperRect the area of the raw wallpaper which is expected to see.
     * @param wallpaperZoom           the factor which is used to scale the raw wallpaper.
     */
    public static Rect calculateCropRect(Context context, Point hostViewSize, Point cropSize,
                                         Point rawWallpaperSize, Rect visibleRawWallpaperRect, float wallpaperZoom) {
        int scrollX = (int) (visibleRawWallpaperRect.left * wallpaperZoom);
        int scrollY = (int) (visibleRawWallpaperRect.top * wallpaperZoom);

        return calculateCropRect(context, wallpaperZoom, rawWallpaperSize, cropSize, hostViewSize,
                scrollX, scrollY);
    }

    /**
     * Calculates the rectangle to crop a wallpaper of the given size, and considering the given
     * scrollX and scrollY offsets
     * @param wallpaperZoom zoom applied to the raw wallpaper image
     * @param wallpaperSize full ("raw") wallpaper size
     * @param defaultCropSurfaceSize @see #getDefaultCropSurfaceSize(Resources, Display)
     * @param targetHostSize target size where the wallpaper will be rendered (eg, the display size)
     * @param scrollX x-axis offset the cropping area from the wallpaper's 0,0 position
     * @param scrollY y-axis offset the cropping area from the wallpaper's 0,0 position
     * @return a Rect representing the area of the wallpaper to crop.
     */
    public static Rect calculateCropRect(Context context, float wallpaperZoom, Point wallpaperSize,
                                         Point defaultCropSurfaceSize, Point targetHostSize, int scrollX, int scrollY) {
        // Calculate Rect of wallpaper in physical pixel terms (i.e., scaled to current zoom).
        int scaledWallpaperWidth = (int) (wallpaperSize.x * wallpaperZoom);
        int scaledWallpaperHeight = (int) (wallpaperSize.y * wallpaperZoom);
        Rect rect = new Rect();
        rect.set(0, 0, scaledWallpaperWidth, scaledWallpaperHeight);

        // Crop rect should start off as the visible screen and then include extra width and height
        // if available within wallpaper at the current zoom.
        Rect cropRect = new Rect(scrollX, scrollY, scrollX + targetHostSize.x,
                scrollY + targetHostSize.y);

        int extraWidth = defaultCropSurfaceSize.x - targetHostSize.x;
        int extraHeightTopAndBottom = (int) ((defaultCropSurfaceSize.y - targetHostSize.y) / 2f);

        // Try to increase size of screenRect to include extra width depending on the layout
        // direction.
        if (isRtl(context)) {
            cropRect.left = Math.max(cropRect.left - extraWidth, rect.left);
        } else {
            cropRect.right = Math.min(cropRect.right + extraWidth, rect.right);
        }

        // Try to increase the size of the cropRect to to include extra height.
        int availableExtraHeightTop = cropRect.top - Math.max(
                rect.top,
                cropRect.top - extraHeightTopAndBottom);
        int availableExtraHeightBottom = Math.min(
                rect.bottom,
                cropRect.bottom + extraHeightTopAndBottom) - cropRect.bottom;

        int availableExtraHeightTopAndBottom =
                Math.min(availableExtraHeightTop, availableExtraHeightBottom);
        cropRect.top -= availableExtraHeightTopAndBottom;
        cropRect.bottom += availableExtraHeightTopAndBottom;

        return cropRect;
    }

    /**
     * Calculates ideal crop surface size for a surface of dimensions maxDim x minDim such that
     * there is room for parallax in both* landscape and portrait screen orientations.
     */
    public static Point calculateCropSurfaceSize(Resources resources, int maxDim, int minDim) {
        final int defaultWidth, defaultHeight;
        if (resources.getConfiguration().smallestScreenWidthDp >= 720) {
            defaultWidth = (int) (maxDim * wallpaperTravelToScreenWidthRatio(maxDim, minDim));
            defaultHeight = maxDim;
        } else {
            defaultWidth = Math.max((int) (minDim * WALLPAPER_SCREENS_SPAN), maxDim);
            defaultHeight = maxDim;
        }

        return new Point(defaultWidth, defaultHeight);
    }

    /**
     * Get the system wallpaper's maximum scale value.
     */
    public static float getSystemWallpaperMaximumScale(Context context) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) return 1.1f;
        return context.getResources()
                .getFloat(Resources.getSystem().getIdentifier(
                        /* name= */ "config_wallpaperMaxScale",
                        /* defType= */ "dimen",
                        /* defPackage= */ "android"));
    }

    /**
     * Returns whether layout direction is RTL (or false for LTR). Since native RTL layout support
     * was added in API 17, returns false for versions lower than 17.
     */
    private static boolean isRtl(Context context) {
        return context.getResources().getConfiguration().getLayoutDirection()
                == View.LAYOUT_DIRECTION_RTL;
    }
}