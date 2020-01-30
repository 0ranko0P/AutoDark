package me.ranko.autodark;

import android.app.Application;
import android.content.Context;

import me.ranko.autodark.core.DebugTree;
import me.ranko.autodark.core.ReleaseTree;
import moe.shizuku.api.ShizukuClientHelper;
import moe.shizuku.api.ShizukuService;
import timber.log.Timber;

public final class AutoDarkApplication extends Application {

    private static boolean v3Failed;

    public static boolean isShizukuV3Failed() {
        return v3Failed;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree.INSTANCE);
        } else {
            Timber.plant(ReleaseTree.INSTANCE);
        }
    }


    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        ShizukuClientHelper.setBinderReceivedListener(() -> {
            if (ShizukuService.getBinder() == null) {
                // ShizukuBinderReceiveProvider started without binder, should never happened
                Timber.d("binder is null");
                v3Failed = true;
            } else {
                try {
                    // test the binder first
                    ShizukuService.pingBinder();
                } catch (Throwable tr) {
                    // blocked by SELinux or server dead, should never happened
                    Timber.i("can't contact with remote", tr);
                    v3Failed = true;
                }
            }
        });
    }
}