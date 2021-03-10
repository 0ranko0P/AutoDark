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

import android.graphics.drawable.GradientDrawable;
import android.view.View;

/**
 * Simple utility class that calculates various sizes relative to the display or current
 * configuration.
 */
public final class SizeCalculator {

    // Suppress default constructor for noninstantiability.
    private SizeCalculator() {
        throw new AssertionError("Can't initialize a SizeCalculator.");
    }

    /**
     * Adjusts the corner radius of the given view by doubling their current values
     *
     * @param view whose background is set to a GradientDrawable
     */
    public static void adjustBackgroundCornerRadius(View view) {
        GradientDrawable background = (GradientDrawable) view.getBackground();
        // Using try/catch because currently GradientDrawable has a bug where when the radii array
        // is null, instead of getCornerRadii returning null, it throws NPE.
        try {
            float[] radii = background.getCornerRadii();
            if (radii == null) {
                return;
            }
            for (int i = 0; i < radii.length; i++) {
                radii[i] *= 2f;
            }
            background = ((GradientDrawable) background.mutate());
            background.setCornerRadii(radii);
            view.setBackground(background);
        } catch (NullPointerException e) {
            //Ignore in this case, since it means the radius was 0.
        }
    }
}
