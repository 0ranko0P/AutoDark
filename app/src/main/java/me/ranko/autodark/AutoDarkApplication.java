package me.ranko.autodark;

import android.app.Application;

import me.ranko.autodark.core.DebugTree;
import me.ranko.autodark.core.ReleaseTree;
import timber.log.Timber;

public final class AutoDarkApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree.INSTANCE);
        } else {
            Timber.plant(ReleaseTree.INSTANCE);
        }
    }
}