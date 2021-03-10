/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.wallpaper.widget;

import android.app.Activity;
import android.app.Application;
import android.app.WallpaperColors;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Point;
import android.text.format.DateFormat;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import com.android.wallpaper.util.ScreenSizeCalculator;
import com.android.wallpaper.util.TimeUtils;
import com.android.wallpaper.util.TimeUtils.TimeTicker;

import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import me.ranko.autodark.R;
import timber.log.Timber;

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;

/**
 * A class to load the custom lockscreen view to the preview screen.
 *
 * [0ranko0P]: Add function attachLifeCycle for widget.
 * */
public final class LockScreenPreviewer implements LifecycleObserver {

    private static final String DEFAULT_DATE_PATTERN = "EEE, MMM d";
    public static final int HINT_SUPPORTS_DARK_TEXT = 1;

    private Activity mActivity;
    private String mDatePattern;
    private TimeTicker mTicker;
    private ImageView mLockIcon;
    private TextView mLockTime;
    private TextView mLockDate;

    public LockScreenPreviewer(Lifecycle lifecycle, Activity activity, ViewGroup previewContainer) {
        this(activity, previewContainer);
        attachLifeCycle(lifecycle, activity);
    }

    public LockScreenPreviewer(Context context, ViewGroup previewContainer) {
        View contentView = LayoutInflater.from(context).inflate(
                R.layout.lock_screen_preview, /* root= */ null);
        ConstraintLayout mContainerView = (ConstraintLayout) contentView;
        mLockIcon = contentView.findViewById(R.id.lock_icon);
        mLockTime = contentView.findViewById(R.id.lock_time);
        mLockDate = contentView.findViewById(R.id.lock_date);
        mDatePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), DEFAULT_DATE_PATTERN);

        Application app = (Application) context.getApplicationContext();
        WindowManager windowManager = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);

        final Display defaultDisplay = windowManager.getDefaultDisplay();
        Point screenSize = ScreenSizeCalculator.getInstance().getScreenSize(defaultDisplay);

        View rootView = previewContainer.getRootView();
        rootView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int cardHeight = previewContainer.getMeasuredHeight();

                // Relayout the content view to match full screen size.
                contentView.measure(
                        makeMeasureSpec(screenSize.x, EXACTLY),
                        makeMeasureSpec(screenSize.y, EXACTLY));
                contentView.layout(0, 0, screenSize.x, screenSize.y);

                // Scale the content view from full screen size to the container(card) size.
                float scale = (float) cardHeight / screenSize.y;
                contentView.setScaleX(scale);
                contentView.setScaleY(scale);
                // The pivot point is centered by default, set to (0, 0).
                contentView.setPivotX(0f);
                contentView.setPivotY(0f);

                previewContainer.addView(
                        contentView,
                        contentView.getMeasuredWidth(),
                        contentView.getMeasuredHeight());
                rootView.removeOnLayoutChangeListener(this);
            }
        });
    }

    public void attachLifeCycle(Lifecycle lifecycle, Activity activity) {
        mActivity = activity;
        lifecycle.addObserver(this);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    @MainThread
    public void onResume() {
        mTicker = TimeTicker.registerNewReceiver(mActivity, this::updateDateTime);
        updateDateTime();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    @MainThread
    public void onPause() {
        if (mActivity != null) {
            mActivity.unregisterReceiver(mTicker);
        }
    }

    /**
     * Sets the content's color based on the wallpaper's {@link WallpaperColors}.
     *
     * @param colors the {@link WallpaperColors} of the wallpaper which the lock screen overlay
     *               will attach to, or {@code null} to use light color as default
     */
    public void setColor(@Nullable WallpaperColors colors) {
        boolean useLightTextColor = colors == null
                || (getColorHints(colors) & HINT_SUPPORTS_DARK_TEXT) == 0;
        int color = mActivity.getColor(useLightTextColor
                ? R.color.text_color_light : R.color.text_color_dark);
        int textShadowColor = mActivity.getColor(useLightTextColor
                ? R.color.smartspace_preview_shadow_color_dark
                : R.color.smartspace_preview_shadow_color_transparent);
        mLockIcon.setImageTintList(ColorStateList.valueOf(color));
        mLockDate.setTextColor(color);
        mLockTime.setTextColor(color);

        mLockDate.setShadowLayer(
                mActivity.getResources().getDimension(
                        R.dimen.smartspace_preview_key_ambient_shadow_blur),
                /* dx = */ 0,
                /* dy = */ 0,
                textShadowColor);
        mLockTime.setShadowLayer(
                mActivity.getResources().getDimension(
                        R.dimen.smartspace_preview_key_ambient_shadow_blur),
                /* dx = */ 0,
                /* dy = */ 0,
                textShadowColor);
    }

    private static Integer getColorHints(WallpaperColors colors) {
        try {
            Method method = WallpaperColors.class.getMethod("getColorHints");
            return (Integer) method.invoke(colors);
        } catch (Exception e) {
            Timber.e(e);
            return 0;
        }
    }

    private void updateDateTime() {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        mLockTime.setText(TimeUtils.getFormattedTime(mActivity, calendar));
        mLockDate.setText(DateFormat.format(mDatePattern, calendar));
    }
}