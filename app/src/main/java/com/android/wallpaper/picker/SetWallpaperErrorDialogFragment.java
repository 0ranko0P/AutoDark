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
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.android.wallpaper.module.WallpaperPersister.Destination;

import java.io.Serializable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

import me.ranko.autodark.R;

import static com.android.wallpaper.picker.LoadWallpaperErrorDialogFragment.getExceptionString;

/**
 * Dialog fragment which communicates a message that setting the wallpaper failed with an option to
 * try again.
 * <p>
 * [0ranko0P] changes:
 * 1. Reuse this fragment for persisting wallpapers.
 * 2. Change message type to exception so it can display error stack.
 */
public final class SetWallpaperErrorDialogFragment extends DialogFragment {

    private static final String ARG_EXCEPTION = "exception";
    private static final String ARG_WALLPAPER_DESTINATION = "destination";

    public static SetWallpaperErrorDialogFragment newInstance(@Nullable Exception e,
                                                              @Destination int wallpaperDestination) {
        SetWallpaperErrorDialogFragment dialogFrag = new SetWallpaperErrorDialogFragment();
        Bundle args = new Bundle();
        if (e != null) {
            args.putSerializable(ARG_EXCEPTION, e);
        }
        args.putInt(ARG_WALLPAPER_DESTINATION, wallpaperDestination);
        dialogFrag.setArguments(args);
        return dialogFrag;
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);
        Serializable exception = requireArguments().getSerializable(ARG_EXCEPTION);
        String message;
        if (exception instanceof TimeoutException) {
            message = getString(R.string.service_wallpaper_failed_timeout);
        } else if (exception instanceof CancellationException) {
            message = getString(android.R.string.cancel);
        } else {
            message = exception == null ? "" : getExceptionString((Exception) exception);
        }
        @Destination final int wallpaperDestination = requireArguments().getInt(ARG_WALLPAPER_DESTINATION);

        AlertDialog.Builder builder = LoadWallpaperErrorDialogFragment.buildShareMessageDialog(this, message)
                .setTitle(R.string.save_wallpaper_error_title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, null);
        if (!(exception instanceof TimeoutException || exception instanceof CancellationException)) {
            builder.setPositiveButton(R.string.app_try_again, (dialogInterface, i) -> {
                // The component hosting this DialogFragment could be either a Fragment or an
                // Activity, so check if a target Fragment was explicitly set--if not then the
                // appropriate Listener would be the containing Activity.
                Fragment fragment = getTargetFragment();
                Activity activity = getActivity();

                Listener callback = (Listener) (fragment == null ? activity : fragment);
                if (callback != null) {
                    callback.onClickTryAgain(wallpaperDestination);
                }
            });
        }
        return builder.create();
    }

    /**
     * Interface which clients of this DialogFragment should implement in order to handle user actions
     * on the dialog's buttons.
     */
    public interface Listener {
        void onClickTryAgain(@Destination int wallpaperDestination);
    }
}