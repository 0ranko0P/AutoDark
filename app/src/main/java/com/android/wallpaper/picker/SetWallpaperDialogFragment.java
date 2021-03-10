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

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;

import com.android.wallpaper.module.WallpaperPersister;

import org.jetbrains.annotations.NotNull;

import me.ranko.autodark.R;

/**
 * Dialog fragment which shows the "Set wallpaper" destination dialog for N+ devices. Lets user
 * choose whether to set the wallpaper on the home screen, lock screen, or both.
 *
 * [0ranko0P]: Make all option visible, AutoDark won't ask liveWallpaper's destination
 */
@SuppressLint("UseCompatLoadingForDrawables")
public final class SetWallpaperDialogFragment extends DialogFragment {

    private Listener mListener;
    private int mTitleResId;
    private boolean mWithItemSelected;

    public SetWallpaperDialogFragment() {
        setRetainInstance(true);
    }

    @Override
    @NotNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);
        Context context = requireContext();
        View layout = View.inflate(context, R.layout.dialog_set_wallpaper, null);

        AlertDialog dialog = new AlertDialog.Builder(context, R.style.SimpleDialogStyle)
                .setTitle(mTitleResId)
                .setView(layout)
                .create();

        Button mSetHomeWallpaperButton = layout.findViewById(R.id.set_home_wallpaper_button);
        mSetHomeWallpaperButton.setOnClickListener(
                v -> onSetWallpaperButtonClick(WallpaperPersister.DEST_HOME_SCREEN));
        mSetHomeWallpaperButton.setCompoundDrawablesRelativeWithIntrinsicBounds(context.getDrawable(R.drawable.ic_home_24px), null, null, null);

        Button mSetLockWallpaperButton = layout.findViewById(R.id.set_lock_wallpaper_button);
        mSetLockWallpaperButton.setOnClickListener(
                v -> onSetWallpaperButtonClick(WallpaperPersister.DEST_LOCK_SCREEN));
        mSetLockWallpaperButton.setCompoundDrawablesRelativeWithIntrinsicBounds(context.getDrawable(R.drawable.ic_lock_outline_24px), null, null, null);

        Button mSetBothWallpaperButton = layout.findViewById(R.id.set_both_wallpaper_button);
        mSetBothWallpaperButton.setOnClickListener(
                v -> onSetWallpaperButtonClick(WallpaperPersister.DEST_BOTH));
        mSetBothWallpaperButton.setCompoundDrawablesRelativeWithIntrinsicBounds(context.getDrawable(R.drawable.ic_smartphone_24px), null, null, null);

        return dialog;
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (mListener != null) {
            mListener.onDialogDismissed(mWithItemSelected);
        }
    }

    public void setTitleResId(@StringRes int titleResId) {
        mTitleResId = titleResId;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    private void onSetWallpaperButtonClick(int destination) {
        mWithItemSelected = true;
        mListener.onSet(destination);
        dismiss();
    }

    /**
     * Interface which clients of this DialogFragment should implement in order to handle user
     * actions on the dialog's clickable elements.
     */
    public interface Listener {
        void onSet(int destination);

        /**
         * Called when the dialog is closed, both because of dismissal and for a selection
         * being set, so it'll be called even after onSet is called.
         *
         * @param withItemSelected true if the dialog is dismissed with item selected
         */
        default void onDialogDismissed(boolean withItemSelected) {}
    }
}