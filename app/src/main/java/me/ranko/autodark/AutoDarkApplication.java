package me.ranko.autodark;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.lifecycle.AndroidViewModel;

import me.ranko.autodark.Services.DarkModeTileService;
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

        DarkModeTileService.setUp(this);
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

    public static boolean checkSelfPermission(Context context, String permission) {
        return context.checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean checkSelfPermission(AndroidViewModel viewModel, String permission) {
        return viewModel.getApplication().checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isOnePlus() {
        return true;//android.os.Build.BRAND.toUpperCase().contains(Constant.BRAND_ONE_PLUS);
    }

    public static boolean isComponentEnabled(Context context, Class<?> target) {
        ComponentName component = new ComponentName(context, target);
        int status = context.getPackageManager().getComponentEnabledSetting(component);
        return status != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }
}