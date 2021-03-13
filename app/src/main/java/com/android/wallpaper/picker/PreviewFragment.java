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
package com.android.wallpaper.picker;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;

import com.android.wallpaper.module.WallpaperPersister.Destination;

import me.ranko.autodark.R;

/**
 * Base Fragment to display the UI for previewing an individual wallpaper
 */
public abstract class PreviewFragment extends AppbarFragment
        implements LoadWallpaperErrorDialogFragment.Listener,
        SetWallpaperErrorDialogFragment.Listener {

    private static final String TAG_SET_WALLPAPER_ERROR_DIALOG_FRAGMENT =
            "set_wallpaper_error_dialog";

    private static final String TAG_LOAD_WALLPAPER_ERROR_DIALOG_FRAGMENT =
            "load_wallpaper_error_dialog";

    protected static final int UNUSED_REQUEST_CODE = 1;

    /**
     * Staged error dialog fragments that were unable to be shown when the hosting activity didn't
     * allow committing fragment transactions.
     */
    private SetWallpaperErrorDialogFragment mStagedSetWallpaperErrorDialogFragment;
    private LoadWallpaperErrorDialogFragment mStagedLoadWallpaperErrorDialogFragment;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    @CallSuper
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(getLayoutResId(), container, false);
        setUpToolbar(view);
        return view;
    }

    @LayoutRes
    protected abstract int getLayoutResId();

    protected int getDeviceDefaultTheme() {
        return android.R.style.Theme_DeviceDefault;
    }

    @Override
    public void onResume() {
        super.onResume();

        // Show the staged 'load wallpaper' or 'set wallpaper' error dialog fragments if there is
        // one that was unable to be shown earlier when this fragment's hosting activity didn't
        // allow committing fragment transactions.
        if (mStagedLoadWallpaperErrorDialogFragment != null) {
            mStagedLoadWallpaperErrorDialogFragment.show(
                    getParentFragmentManager(), TAG_LOAD_WALLPAPER_ERROR_DIALOG_FRAGMENT);
            mStagedLoadWallpaperErrorDialogFragment = null;
        }

        if (mStagedSetWallpaperErrorDialogFragment != null) {
            mStagedSetWallpaperErrorDialogFragment.show(
                    getParentFragmentManager(), TAG_SET_WALLPAPER_ERROR_DIALOG_FRAGMENT);
            mStagedSetWallpaperErrorDialogFragment = null;
        }
    }

    @Override
    public void onDismissError() {
        // no-op
    }

    @Override
    public abstract CharSequence getDefaultTitle();

    protected void finishActivity(boolean success) {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        if (success) {
            activity.setResult(Activity.RESULT_OK);
        }
        activity.overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        activity.finish();
    }

    /**
     * Shows 'load wallpaper' error dialog now or stage it to be shown when the hosting activity is
     * in a state that allows committing fragment transactions.
     */
    protected void showLoadWallpaperErrorDialog(Throwable e) {
        LoadWallpaperErrorDialogFragment dialogFragment =
                LoadWallpaperErrorDialogFragment.newInstance(e);
        dialogFragment.setTargetFragment(this, UNUSED_REQUEST_CODE);

        // Show 'load wallpaper' error dialog now or stage it to be shown when the hosting
        // activity is in a state that allows committing fragment transactions.
        BasePreviewActivity activity = (BasePreviewActivity) getActivity();
        if (activity != null && activity.isSafeToCommitFragmentTransaction()) {
            dialogFragment.show(requireFragmentManager(), TAG_LOAD_WALLPAPER_ERROR_DIALOG_FRAGMENT);
        } else {
            mStagedLoadWallpaperErrorDialogFragment = dialogFragment;
        }
    }

    protected void showSaveWallpaperErrorDialog(@Nullable Exception e, @Destination int destination) {
        SetWallpaperErrorDialogFragment newFragment = SetWallpaperErrorDialogFragment.newInstance(e, destination);
        newFragment.setTargetFragment(this, UNUSED_REQUEST_CODE);

        // Show 'set wallpaper' error dialog now if it's safe to commit fragment transactions,
        // otherwise stage it for later when the hosting activity is in a state to commit fragment
        // transactions.
        BasePreviewActivity activity = (BasePreviewActivity) requireActivity();
        if (activity.isSafeToCommitFragmentTransaction()) {
            newFragment.show(getParentFragmentManager(), TAG_SET_WALLPAPER_ERROR_DIALOG_FRAGMENT);
        } else {
            mStagedSetWallpaperErrorDialogFragment = newFragment;
        }
    }
}