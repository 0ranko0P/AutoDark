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

import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Abstract base class for a wallpaper full-screen preview activity.
 */
public abstract class BasePreviewActivity extends AppCompatActivity implements FragmentTransactionChecker {

    private boolean mIsSafeToCommitFragmentTransaction;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setColorMode(ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT);
        getWindow().setFormat(PixelFormat.TRANSLUCENT);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsSafeToCommitFragmentTransaction = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mIsSafeToCommitFragmentTransaction = false;
    }

    @Override
    public boolean isSafeToCommitFragmentTransaction() {
        return mIsSafeToCommitFragmentTransaction;
    }
}