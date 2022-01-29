package me.ranko.autodark;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import me.ranko.autodark.core.DebugTree;
import me.ranko.autodark.core.ReleaseTree;
import me.ranko.autodark.services.DarkModeTileService;
import rikka.sui.Sui;
import timber.log.Timber;

public final class AutoDarkApplication extends Application {

    public static final boolean isSui = Sui.init(BuildConfig.APPLICATION_ID);

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        HiddenApiBypass.addHiddenApiExemptions("L");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) {
            Timber.plant(new DebugTree());
        } else {
            Timber.plant(ReleaseTree.INSTANCE);
        }

        DarkModeTileService.setUp(this);
    }

    public static boolean isOnePlus() {
        return Build.BRAND.toUpperCase().contains(Constant.BRAND_ONE_PLUS);
    }

    public static boolean isLineageOS() {
        return Build.DISPLAY.startsWith("lineage");
    }

    public static boolean isComponentEnabled(Context context, Class<?> target) {
        ComponentName component = new ComponentName(context, target);
        int status = context.getPackageManager().getComponentEnabledSetting(component);
        return status != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }
}