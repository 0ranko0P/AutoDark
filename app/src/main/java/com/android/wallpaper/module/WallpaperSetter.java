package com.android.wallpaper.module;

import android.app.Activity;
import android.app.IWallpaperManager;
import android.app.ProgressDialog;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentManager;

import com.android.wallpaper.asset.StreamableAsset;
import com.android.wallpaper.model.LiveWallpaperInfo;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.WallpaperPersister.Destination;
import com.android.wallpaper.module.WallpaperPersister.SetWallpaperCallback;
import com.android.wallpaper.picker.SetWallpaperDialogFragment;
import com.android.wallpaper.picker.SetWallpaperDialogFragment.Listener;
import com.bumptech.glide.Glide;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import kotlin.Pair;
import me.ranko.autodark.R;
import me.ranko.autodark.core.ShizukuApi;
import me.ranko.autodark.model.DarkWallpaperInfo;
import me.ranko.autodark.model.Wallpaper;
import rikka.shizuku.Shizuku;
import timber.log.Timber;

import static com.android.wallpaper.module.WallpaperPersister.DEST_BOTH;
import static com.android.wallpaper.module.WallpaperPersister.DEST_HOME_SCREEN;
import static com.android.wallpaper.module.WallpaperPersister.DEST_LOCK_SCREEN;

/**
 * Helper class used to set the current wallpaper. It handles showing the destination request dialog
 * and actually setting the wallpaper on a given destination.
 * It is expected to be instantiated within a Fragment or Activity, and {@link #cleanUp()} should
 * be called from its owner's onDestroy method (or equivalent).
 */
public final class WallpaperSetter {

    private static final String PROGRESS_DIALOG_NO_TITLE = null;
    private static final boolean PROGRESS_DIALOG_INDETERMINATE = true;

    private static final String TAG_SET_WALLPAPER_DIALOG_FRAGMENT = "set_wallpaper_dialog";

    private final WallpaperPersister mWallpaperPersister;
    private ProgressDialog mProgressDialog;
    private Optional<Integer> mCurrentScreenOrientation = Optional.empty();

    public WallpaperSetter(WallpaperPersister wallpaperPersister) {
        mWallpaperPersister = wallpaperPersister;
    }

    /**
     * Cache this cropped wallpaper to the device.
     *
     * @param containerActivity main Activity that owns the current fragment
     * @param wallpaper         info for the actual wallpaper to cache
     * @param wallpaperScale    Scaling factor applied to the source image before setting the
     *                          wallpaper to the device.
     * @param parent            Parent directory that image file will be cached.
     * @param cropRect          Desired crop area of the wallpaper in post-scale units. If null, then the
     *                          wallpaper image will be set without any scaling or cropping.
     * @param callback          optional callback to be notified when the wallpaper is set.
     */
    public void cacheCurrentWallpaper(Activity containerActivity, @NonNull WallpaperInfo wallpaper,
                                      @NonNull File parent, float wallpaperScale,
                                      @NonNull Rect cropRect, @Nullable SetWallpaperCallback callback) {
        // Save current screen rotation so we can temporarily disable rotation while setting the
        // wallpaper and restore after setting the wallpaper finishes.
        saveAndLockScreenOrientationIfNeeded(containerActivity);

        // Clear MosaicView tiles and Glide's cache and pools to reclaim memory for final cropped
        // bitmap.
        Glide.get(containerActivity).clearMemory();

        // ProgressDialog endlessly updates the UI thread, keeping it from going idle which therefore
        // causes Espresso to hang once the dialog is shown.
        if (!containerActivity.isFinishing()) {
            mProgressDialog = new ProgressDialog(containerActivity, R.style.SimpleDialogStyle);

            mProgressDialog.setTitle(PROGRESS_DIALOG_NO_TITLE);
            mProgressDialog.setMessage(containerActivity.getString(
                    R.string.prepare_wallpaper_progress_message));
            mProgressDialog.setIndeterminate(PROGRESS_DIALOG_INDETERMINATE);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }

        mWallpaperPersister.saveCroppedWallpaper(wallpaper.getAsset(containerActivity), wallpaperScale,
                cropRect, parent, new SetWallpaperCallback() {
            @Override
            public void onSuccess(String id) {
                onWallpaperApplied(containerActivity);
                if (callback != null) callback.onSuccess(id);
            }

            @Override
            public void onError(Exception e) {
                onWallpaperApplied(containerActivity);
                if (callback != null) {
                    callback.onError(e);
                } else {
                    Timber.e(e);
                }
            }
        });
    }

    /**
     * Sets given wallpapers to the device.
     *
     * @param context   context.
     * @param home      the actual wallpaper to set.
     * @param lock      optional lockScreen wallpaper.
     * @param callback  optional callback to be notified when the wallpaper is set.
     */

    public void setDarkWallpapers(Context context, DarkWallpaperInfo home,
                                  @Nullable DarkWallpaperInfo lock,
                                  @NonNull SetWallpaperCallback callback) {

        SetWallpaperCallback setHomeCallback = (lock == null) ? callback : id -> {
            try {
                mWallpaperPersister.setIndividualWallpaper((StreamableAsset) lock.getAsset(context), DEST_LOCK_SCREEN, callback);
            } catch (Exception e) {
                callback.onError(e);
            }
        };

        int destination = lock == null ? DEST_BOTH : DEST_HOME_SCREEN;

        try {
            mWallpaperPersister.setIndividualWallpaper((StreamableAsset) home.getAsset(context),
                    destination, setHomeCallback);
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    public void setCurrentLiveWallpaper(Context context, LiveWallpaperInfo wallpaper,
                                        @Nullable SetWallpaperCallback callback) {
        try {
            IWallpaperManager iWallpaperManager = ShizukuApi.INSTANCE.get_IWallpaperManager();
            iWallpaperManager.setWallpaperComponent(wallpaper.getWallpaperComponentName());

            if (callback != null) {
                WallpaperManager wallpaperManager = WallpaperManager.getInstance(context);
                String id = String.valueOf(wallpaperManager.getWallpaperId(WallpaperManager.FLAG_SYSTEM));
                callback.onSuccess(id);
            }
        } catch (RuntimeException e) {
            if (callback != null) {
                callback.onError(e);
            } else {
                Timber.e(e, "Failed setting liveWallpaper: %s.", wallpaper.getWallpaperId());
            }
        }
    }

    /**
     * Show a dialog asking the user for the Wallpaper's destination
     * (eg, "Home screen", "Lock Screen")
     * @param listener {@link SetWallpaperDialogFragment.Listener} that will receive the response.
     * @see Destination
     */
    public void requestDestination(Activity activity, FragmentManager fragmentManager, Listener listener) {
        requestDestination(activity, fragmentManager, R.string.save_wallpaper_dialog_title, listener);
    }

    /**
     * Show a dialog asking the user for the Wallpaper's destination
     * (eg, "Home screen", "Lock Screen")
     * @param listener {@link SetWallpaperDialogFragment.Listener} that will receive the response.
     * @param titleResId title for the dialog
     * @see Destination
     */
    public void requestDestination(Activity activity, FragmentManager fragmentManager,
                                   @StringRes int titleResId, Listener listener) {

        saveAndLockScreenOrientationIfNeeded(activity);
        Listener listenerWrapper = new Listener() {
            @Override
            public void onSet(int destination) {
                if (listener != null) {
                    listener.onSet(destination);
                }
            }

            @Override
            public void onDialogDismissed(boolean withItemSelected) {
                if (!withItemSelected) {
                    restoreScreenOrientationIfNeeded(activity);
                }
                if (listener != null) {
                    listener.onDialogDismissed(withItemSelected);
                }
            }
        };
        SetWallpaperDialogFragment setWallpaperDialog = new SetWallpaperDialogFragment();
        setWallpaperDialog.setTitleResId(titleResId);
        setWallpaperDialog.setListener(listenerWrapper);

        setWallpaperDialog.show(fragmentManager, TAG_SET_WALLPAPER_DIALOG_FRAGMENT);
    }

    private void saveAndLockScreenOrientationIfNeeded(Activity activity) {
        if (!mCurrentScreenOrientation.isPresent()) {
            mCurrentScreenOrientation = Optional.of(activity.getRequestedOrientation());
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        }
    }

    private void restoreScreenOrientationIfNeeded(Activity activity) {
        mCurrentScreenOrientation.ifPresent(orientation -> {
            if (activity.getRequestedOrientation() != orientation) {
                activity.setRequestedOrientation(orientation);
            }
            mCurrentScreenOrientation = Optional.empty();
        });
    }

    private void onWallpaperApplied(Activity containerActivity) {
        cleanUp();
        restoreScreenOrientationIfNeeded(containerActivity);
    }

    /**
     * Call this method to clean up this instance's state.
     */
    public void cleanUp() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }
}