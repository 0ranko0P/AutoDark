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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import me.ranko.autodark.R;

/**
 * Dialog fragment which communicates a message that loading the wallpaper failed with an OK button,
 * when clicked will navigate the user back to the previous activity.
 *
 * [0ranko0P] changes: Show stacktrace to user.
 */
public final class LoadWallpaperErrorDialogFragment extends DialogFragment {

    private static final String ARG_ERROR = "Err";

    @Nullable
    private Throwable e;

    public static LoadWallpaperErrorDialogFragment newInstance(@Nullable Throwable e) {
        LoadWallpaperErrorDialogFragment fragment = new LoadWallpaperErrorDialogFragment();
        if (e != null) {
            Bundle bundle = new Bundle();
            bundle.putSerializable(ARG_ERROR, e);
            fragment.setArguments(bundle);
        }
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Bundle bundle = getArguments();
        if (bundle != null) {
            this.e = (Throwable) bundle.getSerializable(ARG_ERROR);
        }
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.load_wallpaper_error_message)
                .setMessage(getExceptionString(e))
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                Listener callback = (Listener) getTargetFragment();
                                callback.onDismissError();
                                dismiss();
                            }
                        }
                )
                .create();
    }

    public static String getExceptionString(Throwable throwable) {
        if (throwable == null) return "";

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             PrintStream ps = new PrintStream(bos)) {
            throwable.printStackTrace(ps);
            return bos.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    @Override
    public void onDismiss(@NotNull DialogInterface dialogInterface) {
        super.onDismiss(dialogInterface);

        // Treat a dismissal by user click outside the dialog foreground the same as the user clicking
        // "OK" to dismiss the dialog.
        Listener callback = (Listener) getTargetFragment();
        callback.onDismissError();
    }

    /**
     * Interface which clients of this DialogFragment should implement in order to handle user actions
     * on the dialog's buttons.
     */
    public interface Listener {
        void onDismissError();
    }
}