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
package com.android.wallpaper.picker;

import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.android.wallpaper.widget.BottomActionBar;

import me.ranko.autodark.R;

/**
 * Base class for Fragments that own a {@link Toolbar} widget and a {@link BottomActionBar}.
 * <p>
 * A Fragment extending this class is expected to have a {@link Toolbar} in its root view, with id
 * {@link R.id#toolbar}, which can optionally have a TextView with id custom_toolbar_title for
 * the title.
 *
 * @see BottomActionBarFragment
 */
public abstract class AppbarFragment extends BottomActionBarFragment {

    protected Toolbar mToolbar;

    @Nullable
    protected TextView mTitleView;

    /**
     * Configures a toolbar in the given rootView, with id {@code toolbar} and sets its title to
     * the value in Arguments or {@link #getDefaultTitle()}
     */
    public void setUpToolbar(View rootView) {
        mToolbar = rootView.findViewById(R.id.toolbar);

        mTitleView = mToolbar.findViewById(R.id.custom_toolbar_title);
        CharSequence title = getDefaultTitle();

        if (!TextUtils.isEmpty(title)) {
            setTitle(title);
        }
    }

    /**
     * Provides a title for this Fragment's toolbar to be used if none is found in
     * {@link #getArguments()}.
     * Default implementation returns {@code null}.
     */
    public CharSequence getDefaultTitle() {
        return null;
    }

    protected void setTitle(CharSequence title) {
        if (mToolbar == null) {
            return;
        }
        if (mTitleView != null) {
            mToolbar.setTitle(null);
            mTitleView.setText(title);
        } else {
            mToolbar.setTitle(title);
        }

        // Set Activity title to make TalkBack announce title after updating toolbar title.
        if (getActivity() != null) {
            getActivity().setTitle(title);
        }
    }
}