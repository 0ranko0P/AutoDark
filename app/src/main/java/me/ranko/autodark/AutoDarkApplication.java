package me.ranko.autodark;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.UserManager;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import me.ranko.autodark.services.DarkModeTileService;
import me.ranko.autodark.core.DebugTree;
import me.ranko.autodark.core.ReleaseTree;
import rikka.sui.Sui;
import timber.log.Timber;
import me.weishu.reflection.Reflection;

public final class AutoDarkApplication extends Application {

    public static final boolean isSui = Sui.init(BuildConfig.APPLICATION_ID);

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Reflection.unseal(base);
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

    public static List<Integer> listAllUserId(Context context) {
        UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        List<UserHandle> users = userManager.getUserProfiles();
        ArrayList<Integer> res = new ArrayList<>(users.size());
        Pattern findInt = Pattern.compile("\\D+");
        for (UserHandle user : users) {
            String idStr = findInt.matcher(user.toString()).replaceAll("");
            res.add(Integer.valueOf(idStr));
        }
        return res;
    }

    public static boolean isOnePlus() {
        return android.os.Build.BRAND.toUpperCase().contains(Constant.BRAND_ONE_PLUS);
    }

    public static boolean isComponentEnabled(Context context, Class<?> target) {
        ComponentName component = new ComponentName(context, target);
        int status = context.getPackageManager().getComponentEnabledSetting(component);
        return status != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }
}