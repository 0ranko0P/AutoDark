package me.ranko.autodark;

import android.Manifest;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class Constant {

    public static final String ANDROID_PACKAGE = "android";

    /**
     * Available when internal storage is encrypted
     * SystemServer can initialize block list here
     * */
    public static final String APP_DATA_DIR = "/data/user_de/0/" + BuildConfig.APPLICATION_ID;

    public static final Path BLOCK_LIST_PATH = Paths.get(APP_DATA_DIR + File.separator + "block.txt");

    public static final Path BLOCK_LIST_SYSTEM_APP_CONFIG_PATH = Paths.get( APP_DATA_DIR + File.separator + "blockSystemApp");

    public static final Path BLOCK_LIST_INPUT_METHOD_CONFIG_PATH = Paths.get( APP_DATA_DIR + File.separator + "blockIME");

    public static final String BRAND_ONE_PLUS = "OnePlus".toUpperCase();

    public static final String PERMISSION_DARK_BROADCAST = "me.ranko0p.permission.RECEIVE_DARK_BROADCAST";

    public static final String SP_KEY_MASTER_SWITCH = "switch";

    public static final String SP_AUTO_TIME_SUNRISE = "sunrise";
    public static final String SP_AUTO_TIME_SUNSET = "sunset";

    public static final String SP_RESTRICTED_SILENCE = "silence";

    public static final String COMMAND_GRANT_PM = "pm grant " + BuildConfig.APPLICATION_ID + " " + Manifest.permission.WRITE_SECURE_SETTINGS;
    public static final String COMMAND_GRANT_ADB = "adb -d shell " + COMMAND_GRANT_PM;

    /**
     * Force-dark mode.
     * <p>
     * Return <strong>null</strong> when force-dark is untouched.
     * </p>
     **/
    public static final String SYSTEM_PROP_FORCE_DARK = "debug.hwui.force_dark";

    public static final String SYSTEM_PROP_HOOK_SYSTEM_APPS = "debug.hwui.hook_sys_app";

    public static final String SYSTEM_PROP_HOOK_INPUT_METHOD = "debug.hwui.hook_ime";

    public static final String SYSTEM_SECURE_PROP_DARK_MODE = "ui_night_mode";

    public static final String COMMAND_SET_FORCE_DARK_ON = "setprop " + SYSTEM_PROP_FORCE_DARK + " true";
    public static final String COMMAND_SET_FORCE_DARK_OFF = "setprop " + SYSTEM_PROP_FORCE_DARK + " false";

    public static final int JOB_STATUS_PENDING = 0x00C0;
    public static final int JOB_STATUS_FAILED = JOB_STATUS_PENDING << 1;
    public static final int JOB_STATUS_SUCCEED = JOB_STATUS_FAILED << 1;
}
