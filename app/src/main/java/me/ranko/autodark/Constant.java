package me.ranko.autodark;

import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class Constant {

    public static final String BRAND_ONE_PLUS = "OnePlus";

    public static final String SP_KEY_MASTER_SWITCH = "switch";

    public static final String SP_AUTO_TIME_SUNRISE = "sunrise";
    public static final String SP_AUTO_TIME_SUNSET = "sunset";

    public static final String SP_AUTO_mode = "dark_mode_auto";

    public static final String SP_RESTRICTED_SILENCE = "silence";

    public static final String PERMISSION_WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS";

    public static final String COMMAND_GRANT_PM = "pm grant " + BuildConfig.APPLICATION_ID + " " + PERMISSION_WRITE_SECURE_SETTINGS;
    public static final String COMMAND_GRANT_ADB = "adb -d shell " + COMMAND_GRANT_PM;

    /**
     * Force-dark mode.
     * <p>
     * Return <strong>null</strong> on some device while force-dark is <strong>false</strong>.
     * </p>
     **/
    public static final String SYSTEM_PROP_FORCE_DARK = "debug.hwui.force_dark";

    public static final String SYSTEM_SECURE_PROP_DARK_MODE = "ui_night_mode";

    public static final String COMMAND_GET_FORCE_DARK = "getprop " + SYSTEM_PROP_FORCE_DARK;
    public static final String COMMAND_SET_FORCE_DARK_ON = "setprop " + SYSTEM_PROP_FORCE_DARK + " true";
    public static final String COMMAND_SET_FORCE_DARK_OFF = "setprop " + SYSTEM_PROP_FORCE_DARK + " false";

    public static final String DARK_PREFERENCE_AUTO = "dark_mode_auto";
    public static final String DARK_PREFERENCE_START = "dark_mode_time_start";
    public static final String DARK_PREFERENCE_END = "dark_mode_time_end";

    public static final String DARK_PREFERENCE_FORCE = "dark_mode_force";

    public static final int JOB_STATUS_PENDING = 0x00C0;
    public static final int JOB_STATUS_FAILED = JOB_STATUS_PENDING << 1;
    public static final int JOB_STATUS_SUCCEED = JOB_STATUS_FAILED << 1;

    @StringDef({DARK_PREFERENCE_START, DARK_PREFERENCE_END})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DARK_JOB_TYPE {}
}
