package me.ranko.autodark;

import static me.ranko.autodark.core.DarkModeSettingsKt.SYSTEM_PROP_FORCE_DARK;

public final class Constant {

    public static final String PERMISSION_WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS";

    public static final String COMMAND_GRANT_ROOT = "pm grant me.ranko.autodark android.permission.WRITE_SECURE_SETTINGS";
    public static final String COMMAND_GRANT_ADB = "adb -d shell " + COMMAND_GRANT_ROOT;

    public static final String COMMAND_GET_FORCE_DARK = "getprop " + SYSTEM_PROP_FORCE_DARK;
    public static final String COMMAND_SET_FORCE_DARK_ON = "setprop " + SYSTEM_PROP_FORCE_DARK + " true";
    public static final String COMMAND_SET_FORCE_DARK_OFF = "setprop " + SYSTEM_PROP_FORCE_DARK + " false";

    public static final int JOB_STATUS_PENDING = 0x00C0;
    public static final int JOB_STATUS_FAILED = JOB_STATUS_PENDING << 1;
    public static final int JOB_STATUS_SUCCEED = JOB_STATUS_FAILED << 1;
}
