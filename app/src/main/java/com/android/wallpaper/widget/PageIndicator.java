/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import me.ranko.autodark.R;
import timber.log.Timber;

/**
 * Page indicator widget, based on QS's page indicator:
 *
 * Based on QS PageIndicator
 * Path: frameworks/base/packages/SystemUI/src/com/android/systemui/qs/PageIndicator.java
 */
public final class PageIndicator extends ViewGroup {

    private static final String TAG = "PageIndicator";
    private static final boolean DEBUG = false;

    // The size of a single dot in relation to the whole animation.
    private static final float SINGLE_SCALE = .4f;

    static final float MINOR_ALPHA = .42f;

    private final ArrayList<Integer> mQueuedPositions = new ArrayList<>();

    private final int mPageIndicatorWidth;
    private final int mPageIndicatorHeight;
    private final int mPageDotWidth;

    private int mPosition = -1;
    private boolean mAnimating;

    private static Method sMethodForceAnimationOnUI = null;
    private final Animatable2.AnimationCallback mAnimationCallback =
            new Animatable2.AnimationCallback() {

                @Override
                public void onAnimationEnd(Drawable drawable) {
                    super.onAnimationEnd(drawable);
                    if (drawable instanceof AnimatedVectorDrawable) {
                        ((AnimatedVectorDrawable) drawable).unregisterAnimationCallback(
                                mAnimationCallback);
                    }
                    if (DEBUG) {
                        Timber.d( "onAnimationEnd - queued: %s", mQueuedPositions.size());
                    }
                    mAnimating = false;
                    if (mQueuedPositions.size() != 0) {
                        setPosition(mQueuedPositions.remove(0));
                    }
                }
            };


    public PageIndicator(Context context, AttributeSet attrs) {
        super(context, attrs);
        mPageIndicatorWidth =
                (int) context.getResources().getDimension(R.dimen.preview_indicator_width);
        mPageIndicatorHeight =
                (int) context.getResources().getDimension(R.dimen.preview_indicator_height);
        mPageDotWidth = (int) (mPageIndicatorWidth * SINGLE_SCALE);
    }

    public void setNumPages(int numPages) {
        setVisibility(numPages > 1 ? View.VISIBLE : View.INVISIBLE);
        if (mAnimating) {
            Timber.w("setNumPages during animation");
        }
        while (numPages < getChildCount()) {
            removeViewAt(getChildCount() - 1);
        }

        ColorStateList color = ColorStateList.valueOf(getResources().getColor(R.color.primary, getContext().getTheme()));
        while (numPages > getChildCount()) {
            ImageView v = new ImageView(getContext());
            v.setImageResource(R.drawable.minor_a_b);
            v.setImageTintList(color);
            addView(v, new LayoutParams(mPageIndicatorWidth, mPageIndicatorHeight));
        }
        // Refresh state.
        setIndex(mPosition >> 1);
    }

    public void setLocation(float location) {
        int index = (int) location;
        setContentDescription(getContext().getString(R.string.accessibility_preview_pager,
                (index + 1), getChildCount()));
        int position = index << 1 | ((location != index) ? 1 : 0);
        if (DEBUG) {
            Timber.d("setLocation " + location + " " + index + " " + position);
        }
        int lastPosition = mPosition;
        if (mQueuedPositions.size() != 0) {
            lastPosition = mQueuedPositions.get(mQueuedPositions.size() - 1);
        }
        if (DEBUG) {
            Timber.d(position + " " + lastPosition);
        }
        if (position == lastPosition) return;
        if (mAnimating) {
            if (DEBUG) {
                Timber.d("Queueing transition to %s", Integer.toHexString(position));
            }
            mQueuedPositions.add(position);
            return;
        }

        setPosition(position);
    }

    private void setPosition(int position) {
        if (mPosition >= 0 && Math.abs(mPosition - position) == 1) {
            animate(mPosition, position);
        } else {
            if (DEBUG) {
                Timber.d("Skipping animation " + mPosition + " " + position);
            }
            setIndex(position >> 1);
        }
        mPosition = position;
    }

    private void setIndex(int index) {
        final int N = getChildCount();
        for (int i = 0; i < N; i++) {
            ImageView v = (ImageView) getChildAt(i);
            // Clear out any animation positioning.
            v.setTranslationX(0);
            v.setImageResource(R.drawable.major_a_b);
            v.setAlpha(getAlpha(i == index));
        }
    }

    private void animate(int from, int to) {
        if (DEBUG) {
           Timber.d( "Animating from " + Integer.toHexString(from) + " to "
                    + Integer.toHexString(to));
        }
        int fromIndex = from >> 1;
        int toIndex = to >> 1;

        // Set the position of everything, then we will manually control the two views involved
        // in the animation.
        setIndex(fromIndex);

        boolean fromTransition = (from & 1) != 0;
        boolean isAState = fromTransition ? from > to : from < to;
        int firstIndex = Math.min(fromIndex, toIndex);
        int secondIndex = Math.max(fromIndex, toIndex);
        if (secondIndex == firstIndex) {
            secondIndex++;
        }
        ImageView first = (ImageView) getChildAt(firstIndex);
        ImageView second = (ImageView) getChildAt(secondIndex);
        if (first == null || second == null) {
            // may happen during reInflation or other weird cases
            return;
        }
        // Lay the two views on top of each other.
        second.setTranslationX(first.getX() - second.getX());

        playAnimation(first, getTransition(fromTransition, isAState, false));
        first.setAlpha(getAlpha(false));

        playAnimation(second, getTransition(fromTransition, isAState, true));
        second.setAlpha(getAlpha(true));

        mAnimating = true;
    }

    private float getAlpha(boolean isMajor) {
        return isMajor ? 1 : MINOR_ALPHA;
    }

    private void playAnimation(ImageView imageView, int res) {
        Drawable drawable = getContext().getDrawable(res);
        if (!(drawable instanceof AnimatedVectorDrawable)) {
            return;
        }
        final AnimatedVectorDrawable avd = (AnimatedVectorDrawable) drawable;
        imageView.setImageDrawable(avd);
        try {
            forceAnimationOnUI(avd);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            Timber.e(e, "Catch an exception in playAnimation");
        }
        avd.registerAnimationCallback(mAnimationCallback);
        avd.start();
    }

    private void forceAnimationOnUI(AnimatedVectorDrawable avd)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if (sMethodForceAnimationOnUI == null) {
            sMethodForceAnimationOnUI = AnimatedVectorDrawable.class.getMethod(
                    "forceAnimationOnUI");
        }
        if (sMethodForceAnimationOnUI != null) {
            sMethodForceAnimationOnUI.invoke(avd);
        }
    }

    private int getTransition(boolean fromB, boolean isMajorAState, boolean isMajor) {
        if (isMajor) {
            if (fromB) {
                if (isMajorAState) {
                    return R.drawable.major_b_a_animation;
                } else {
                    return R.drawable.major_b_c_animation;
                }
            } else {
                if (isMajorAState) {
                    return R.drawable.major_a_b_animation;
                } else {
                    return R.drawable.major_c_b_animation;
                }
            }
        } else {
            if (fromB) {
                if (isMajorAState) {
                    return R.drawable.minor_b_c_animation;
                } else {
                    return R.drawable.minor_b_a_animation;
                }
            } else {
                if (isMajorAState) {
                    return R.drawable.minor_c_b_animation;
                } else {
                    return R.drawable.minor_a_b_animation;
                }
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int N = getChildCount();
        if (N == 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        final int widthChildSpec = MeasureSpec.makeMeasureSpec(mPageIndicatorWidth,
                MeasureSpec.EXACTLY);
        final int heightChildSpec = MeasureSpec.makeMeasureSpec(mPageIndicatorHeight,
                MeasureSpec.EXACTLY);
        for (int i = 0; i < N; i++) {
            getChildAt(i).measure(widthChildSpec, heightChildSpec);
        }
        int width = (mPageIndicatorWidth - mPageDotWidth) * (N - 1) + mPageDotWidth;
        setMeasuredDimension(width, mPageIndicatorHeight);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int N = getChildCount();
        if (N == 0) {
            return;
        }
        for (int i = 0; i < N; i++) {
            int left = (mPageIndicatorWidth - mPageDotWidth) * i;
            getChildAt(i).layout(left, 0, mPageIndicatorWidth + left, mPageIndicatorHeight);
        }
    }
}