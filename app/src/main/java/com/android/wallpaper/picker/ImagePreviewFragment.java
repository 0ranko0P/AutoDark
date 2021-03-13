/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.wallpaper.picker;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.WallpaperPersister;
import com.android.wallpaper.module.WallpaperSetter;
import com.android.wallpaper.util.ScreenSizeCalculator;
import com.android.wallpaper.util.WallpaperCropUtils;
import com.android.wallpaper.widget.BottomActionBar;
import com.android.wallpaper.widget.LockScreenPreviewer;
import com.android.wallpaper.widget.WallpaperColorsLoader;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.google.android.material.tabs.TabLayout;

import java.util.Locale;
import java.util.Objects;

import me.ranko.autodark.R;
import me.ranko.autodark.Utils.ViewUtil;
import me.ranko.autodark.model.CroppedWallpaperInfo;
import timber.log.Timber;

import static com.android.wallpaper.module.WallpaperPersister.DEST_BOTH;
import static com.android.wallpaper.widget.BottomActionBar.BottomAction.APPLY;
import static com.android.wallpaper.widget.BottomActionBar.BottomAction.EDIT;
import static me.ranko.autodark.ui.StandalonePreviewActivity.ARG_NO_DESTINATION;
import static me.ranko.autodark.ui.StandalonePreviewActivity.ARG_WALLPAPER;

/**
 * Fragment which displays the UI for previewing an individual static wallpaper and its attribution
 * information.
 *
 * [0ranko0P] changes: Remove all SurfaceControlViewHost code.
 */
public final class ImagePreviewFragment extends PreviewFragment
        implements SetWallpaperDialogFragment.Listener {

    public interface WallPaperPickerListener {
        void onWallpaperCropped(CroppedWallpaperInfo wallpaperInfo);
    }

    private WallpaperInfo mWallpaper;
    private WallpaperSetter mWallpaperSetter;
    private boolean noDestination;

    private static final float DEFAULT_WALLPAPER_MAX_ZOOM = 8f;

    private WallPaperPickerListener mPickerListener;

    private ContentLoadingProgressBar mLoadingProgressBar;
    private SubsamplingScaleImageView mFullResImageView;

    private Point mRawWallpaperSize; // Native size of wallpaper image.
    private TouchForwardingLayout mTouchForwardingLayout;
    private ConstraintLayout mContainer;
    private FrameLayout mWallpaperSurface;
    private LockScreenPreviewer mLockScreenPreviewer;
    private ViewGroup mLockPreviewContainer;

    /**
     * Creates and returns new instance of {@link ImagePreviewFragment} with the provided wallpaper
     * set as an argument.
     */
    public static PreviewFragment newInstance(WallpaperInfo wallpaper, boolean noDestination) {
        Bundle args = new Bundle();
        args.putParcelable(ARG_WALLPAPER, wallpaper);
        args.putBoolean(ARG_NO_DESTINATION, noDestination);

        PreviewFragment fragment = new ImagePreviewFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //noinspection ResourceType
        mWallpaper = Objects.requireNonNull(requireArguments().getParcelable(ARG_WALLPAPER));
        noDestination = requireArguments().getBoolean(ARG_NO_DESTINATION, false);
        Context appContext = requireContext().getApplicationContext();
        mWallpaperSetter = new WallpaperSetter(new WallpaperPersister(appContext));
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof WallPaperPickerListener) {
            mPickerListener = (WallPaperPickerListener) context;
        } else {
            throw new RuntimeException("Must implement " + WallPaperPickerListener.class.getName());
        }
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_image_preview_v2;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = Objects.requireNonNull(super.onCreateView(inflater, container, savedInstanceState));

        mLoadingProgressBar = view.findViewById(R.id.loading_indicator);
        mLoadingProgressBar.show();
        mContainer = view.findViewById(R.id.container);
        mTouchForwardingLayout = mContainer.findViewById(R.id.touch_forwarding_layout);

        Activity activity = requireActivity();
        Point mScreenSize = ScreenSizeCalculator.getInstance().getScreenSize(
                activity.getWindowManager().getDefaultDisplay());
        // Set aspect ratio on the preview card dynamically.
        ConstraintSet set = new ConstraintSet();
        set.clone(mContainer);
        String ratio = String.format(Locale.US, "%d:%d", mScreenSize.x, mScreenSize.y);
        set.setDimensionRatio(mTouchForwardingLayout.getId(), ratio);
        set.applyTo(mContainer);

        mWallpaperSurface = mTouchForwardingLayout.findViewById(R.id.wallpaper_container);
        mFullResImageView = mWallpaperSurface.findViewById(R.id.full_res_image);

        mLockPreviewContainer = mContainer.findViewById(R.id.lock_screen_preview_container);
        mLockScreenPreviewer = new LockScreenPreviewer(getLifecycle(), activity, mLockPreviewContainer);

        TabLayout tabs = inflater.inflate(R.layout.full_preview_tabs,
                view.findViewById(R.id.toolbar_tabs_container))
                .findViewById(R.id.full_preview_tabs);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                updateScreenPreview(tab.getPosition() == 0);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        // The TabLayout only contains below tabs, see: full_preview_tabs.xml
        // 0. Home tab
        // 1. Lock tab
        tabs.getTabAt(0).select();
        updateScreenPreview(true);
        mTouchForwardingLayout.setTargetView(mFullResImageView);

        setUpLoadingIndicator();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        WallpaperColorsLoader.getWallpaperColors(getContext(),
                mWallpaper.getThumbAsset(getContext()),
                mLockScreenPreviewer::setColor);
    }

    protected boolean isLoaded() {
        return mFullResImageView.hasImage();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mLoadingProgressBar != null) {
            mLoadingProgressBar.hide();
        }
        mFullResImageView.recycle();
        mWallpaperSetter.cleanUp();
        mWallpaperSetter = null;
    }

    @Override
    public CharSequence getDefaultTitle() {
        return getString(R.string.app_crop);
    }

    @Override
    protected void onBottomActionBarReady(@NonNull BottomActionBar bottomActionBar) {
        bottomActionBar.showActionsOnly(EDIT, APPLY);
        bottomActionBar.setActionClickListener(EDIT, v ->
                setEditingEnabled(bottomActionBar.isActionSelected(EDIT))
        );
        bottomActionBar.hideActions();
        bottomActionBar.setActionSelectedListener(EDIT, this::setEditingEnabled);
        bottomActionBar.setActionClickListener(APPLY, this::onSetWallpaperClicked);

        // Update target view's accessibility param since it will be blocked by the bottom sheet
        // when expanded.
        bottomActionBar.setAccessibilityCallback(new BottomActionBar.AccessibilityCallback() {
            @Override
            public void onBottomSheetCollapsed() {
                mContainer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            }

            @Override
            public void onBottomSheetExpanded() {
                mContainer.setImportantForAccessibility(
                        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
        });

        // Will trigger onActionSelected callback to update the editing state.
        bottomActionBar.setDefaultSelectedButton(EDIT);
        bottomActionBar.show();

        bottomActionBar.disableActions();
        mWallpaper.getAsset(requireContext()).decodeRawDimensionsAsync(new Asset.DimensionsReceiver() {
            @Override
            public void onDimensionsDecoded(@NonNull Point dimensions) {
                // Don't continue loading the wallpaper if the Fragment is detached.
                if (getActivity() == null) return;

                bottomActionBar.enableActions();
                mRawWallpaperSize = dimensions;
                initFullResView();
            }

            @Override
            public void onError(@Nullable Exception e) {
                showLoadWallpaperErrorDialog(e);
            }
        });
    }

    /**
     * Initializes MosaicView by initializing tiling, setting a fallback page bitmap, and
     * initializing a zoom-scroll observer and click listener.
     */
    private void initFullResView() {
        // Minimum scale will only be respected under this scale type.
        mFullResImageView.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CUSTOM);
        // When we set a minimum scale bigger than the scale with which the full image is shown,
        // disallow user to pan outside the view we show the wallpaper in.
        mFullResImageView.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE);

        // Then set a fallback "page bitmap" to cover the whole MosaicView, which is an actual
        // (lower res) version of the image to be displayed.
        Point targetPageBitmapSize = new Point(mRawWallpaperSize);
        mWallpaper.getAsset(requireContext()).decodeBitmapAsync(targetPageBitmapSize.x, targetPageBitmapSize.y,
                new Asset.BitmapReceiver() {
            @Override
            public void onBitmapDecoded(@NonNull Bitmap pageBitmap) {
                // Check that the activity is still around since the decoding task started.
                if (getActivity() == null) return;

                // Some of these may be null depending on if the Fragment is paused, stopped,
                // or destroyed.
                if (mLoadingProgressBar != null) {
                    mLoadingProgressBar.hide();
                }

                if (mFullResImageView != null) {
                    // Set page bitmap.
                    mFullResImageView.setImage(ImageSource.bitmap(pageBitmap));

                    setDefaultWallpaperZoomAndScroll();
                    crossFadeInMosaicView();
                }
                getActivity().invalidateOptionsMenu();
            }

            @Override
            public void onError(Exception e) {
                showLoadWallpaperErrorDialog(e);
            }
        });
    }

    /**
     * Makes the MosaicView visible with an alpha fade-in animation while fading out the loading
     * indicator.
     */
    private void crossFadeInMosaicView() {
        long animTime = getResources().getInteger(android.R.integer.config_mediumAnimTime);

        mFullResImageView.setAlpha(0f);
        mFullResImageView.animate()
                .alpha(1f)
                .setInterpolator(new FastOutSlowInInterpolator())
                .setDuration(animTime);

        mLoadingProgressBar.animate()
                .alpha(0f)
                .setDuration(animTime)
                .setInterpolator(new AccelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (mLoadingProgressBar != null) {
                            mLoadingProgressBar.hide();
                        }
                    }
                });
    }

    /**
     * Configure loading indicator with a MaterialProgressDrawable.
     */
    protected void setUpLoadingIndicator() {
        mLoadingProgressBar.setProgressTintList(ColorStateList.valueOf(ViewUtil.INSTANCE.getAttrColor(
                new ContextThemeWrapper(requireContext(), getDeviceDefaultTheme()),
                android.R.attr.colorAccent)));
        mLoadingProgressBar.show();
    }

    /**
     * Sets the default wallpaper zoom and scroll position based on a "crop surface" (with extra
     * width to account for parallax) superimposed on the screen. Shows as much of the wallpaper as
     * possible on the crop surface and align screen to crop surface such that the default preview
     * matches what would be seen by the user in the left-most home screen.
     *
     * <p>This method is called once in the Fragment lifecycle after the wallpaper asset has loaded
     * and rendered to the layout.
     */
    private void setDefaultWallpaperZoomAndScroll() {
        // Determine minimum zoom to fit maximum visible area of wallpaper on crop surface.
        int cropWidth = mWallpaperSurface.getMeasuredWidth();
        int cropHeight = mWallpaperSurface.getMeasuredHeight();
        Point crop = new Point(cropWidth, cropHeight);
        Rect visibleRawWallpaperRect = WallpaperCropUtils.calculateVisibleRect(mRawWallpaperSize, crop);

        final PointF centerPosition = WallpaperCropUtils.calculateDefaultCenter(requireContext(),
                mRawWallpaperSize, visibleRawWallpaperRect);

        Point visibleRawWallpaperSize = new Point(visibleRawWallpaperRect.width(),
                visibleRawWallpaperRect.height());

        final float defaultWallpaperZoom = WallpaperCropUtils.calculateMinZoom(
                visibleRawWallpaperSize, crop);

        // Set min wallpaper zoom and max zoom on MosaicView widget.
        mFullResImageView.setMaxScale(Math.max(DEFAULT_WALLPAPER_MAX_ZOOM, defaultWallpaperZoom));
        mFullResImageView.setMinScale(defaultWallpaperZoom);

        // Set center to composite positioning between scaled wallpaper and screen.
        mFullResImageView.setScaleAndCenter(defaultWallpaperZoom, centerPosition);
    }

    private Rect calculateCropRect() {
        float wallpaperZoom = mFullResImageView.getScale();
        Context context = requireContext().getApplicationContext();

        Rect visibleFileRect = new Rect();
        mFullResImageView.visibleFileRect(visibleFileRect);

        int cropWidth = mWallpaperSurface.getMeasuredWidth();
        int cropHeight = mWallpaperSurface.getMeasuredHeight();
        int maxCrop = Math.max(cropWidth, cropHeight);
        int minCrop = Math.min(cropWidth, cropHeight);
        Point hostViewSize = new Point(cropWidth, cropHeight);

        Resources res = context.getResources();
        Point cropSurfaceSize = WallpaperCropUtils.calculateCropSurfaceSize(res, maxCrop, minCrop);
        WallpaperCropUtils.scaleSize(context, hostViewSize);
        WallpaperCropUtils.scaleSize(context, cropSurfaceSize);

        WallpaperCropUtils.adjustCropRect(context, visibleFileRect, false);

        return WallpaperCropUtils.calculateCropRect(context, hostViewSize,
                cropSurfaceSize, mRawWallpaperSize, visibleFileRect, wallpaperZoom);
    }

    @Override
    public void onSet(int destination) {
        mWallpaperSetter.cacheCurrentWallpaper(requireActivity(), mWallpaper, requireContext().getCacheDir(),
                mFullResImageView.getScale(), calculateCropRect(), new WallpaperPersister.SetWallpaperCallback() {
            @Override
            public void onSuccess(String id) {
                mPickerListener.onWallpaperCropped(new CroppedWallpaperInfo(id, destination));
            }

            @Override
            public void onError(@Nullable Exception e) {
                Timber.e(e);
                showSaveWallpaperErrorDialog(LoadWallpaperErrorDialogFragment.getExceptionString(e), destination);
            }
        });
    }

    @Override
    public void onClickTryAgain(int wallpaperDestination) {
        onSet(wallpaperDestination);
    }

    @Override
    public void onDialogDismissed(boolean withItemSelected) {
        if (mBottomActionBar != null)
            mBottomActionBar.deselectAction(APPLY);
    }

    @Override
    public void onDismissError() {
        finishActivity(false);
    }

    private void setEditingEnabled(boolean enabled) {
        mTouchForwardingLayout.setForwardingEnabled(enabled);
    }

    private void onSetWallpaperClicked(View unused) {
        if (noDestination) {
            this.onSet(DEST_BOTH);
        } else {
            mWallpaperSetter.requestDestination(getActivity(), getParentFragmentManager(), this);
        }
    }

    private void updateScreenPreview(boolean isHomeSelected) {
        mLockPreviewContainer.setVisibility(isHomeSelected ? View.INVISIBLE : View.VISIBLE);
    }
}