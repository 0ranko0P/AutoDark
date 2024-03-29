package me.ranko.autodark.core

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemProperties
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.annotation.StringDef
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.ranko.autodark.AutoDarkApplication
import me.ranko.autodark.Constant.*
import me.ranko.autodark.R
import me.ranko.autodark.Utils.DarkLocationUtil
import me.ranko.autodark.Utils.DarkTimeUtil
import me.ranko.autodark.Utils.DarkTimeUtil.getPersistFormattedString
import me.ranko.autodark.Utils.DarkTimeUtil.getTodayOrNextDay
import me.ranko.autodark.Utils.ShellJobUtil
import me.ranko.autodark.receivers.DarkModeAlarmReceiver
import me.ranko.autodark.ui.DarkWallpaperHelper
import me.ranko.autodark.ui.MainFragment.Companion.DARK_PREFERENCE_END
import me.ranko.autodark.ui.MainFragment.Companion.DARK_PREFERENCE_FORCE_ROOT
import me.ranko.autodark.ui.MainFragment.Companion.DARK_PREFERENCE_START
import me.ranko.autodark.ui.Preference.DarkDisplayPreference
import timber.log.Timber
import java.time.LocalTime

interface DarkPreferenceSupplier {
    fun get(@DarkPreferenceType type: String): DarkDisplayPreference
}

@StringDef(DARK_PREFERENCE_START, DARK_PREFERENCE_END)
@kotlin.annotation.Retention(AnnotationRetention.SOURCE)
annotation class DarkPreferenceType

/**
 *  Dark mode Controller
 *
 *  Listener on [DarkDisplayPreference]
 *
 * @author  0ranko0P
 * */
class DarkModeSettings private constructor(private val context: Application) :
    OnPreferenceChangeListener,
    DefaultLifecycleObserver {

    companion object {
        private const val PARAM_ALARM_TYPE = "ALARM_TYPE"
        private const val PARAM_ALARM_TIME = "ALARM_TIME"

        private const val REQUEST_ALARM_START = 0x00B0
        private const val REQUEST_ALARM_END = REQUEST_ALARM_START.shl(1)

        private const val SP_AUTO_mode = "dark_mode_auto"

        @Volatile
        private var INSTANCE: DarkModeSettings? = null

        @JvmStatic
        fun getInstance(context: Context): DarkModeSettings {
            if (INSTANCE == null) {
                synchronized(DarkLocationUtil::class.java) {
                    if (INSTANCE == null) {
                        INSTANCE = DarkModeSettings(context.applicationContext as Application)
                    }
                }
            }
            return INSTANCE!!
        }

        /**
         * Configure system force-dark mode
         *
         * Experimental feature, System will reset this mode after reboot.
         *
         * @return  false If failed to set dark mode
         *
         * @see     SYSTEM_PROP_FORCE_DARK
         * @see     COMMAND_SET_FORCE_DARK_ON
         * */
        suspend fun setForceDark(enabled: Boolean): Boolean {
            if (AutoDarkApplication.isSui) {
                return ShizukuApi.setForceDark(enabled)
            }

            return try {
                // Run: set force mode && get force mode
                val command = if (enabled) COMMAND_SET_FORCE_DARK_ON else COMMAND_SET_FORCE_DARK_OFF
                ShellJobUtil.runSudoJob(command)
                // check return value
                SystemProperties.getBoolean(SYSTEM_PROP_FORCE_DARK, false) == enabled
            } catch (e: Exception) {
                Timber.i("Error: ${e.localizedMessage}")
                false
            }
        }
    }

    private val mManager: UiModeManager by lazy(LazyThreadSafetyMode.NONE) { context.getSystemService(UiModeManager::class.java)!! }

    private val mAlarmManager: AlarmManager by lazy(LazyThreadSafetyMode.NONE) { context.getSystemService(Activity.ALARM_SERVICE) as AlarmManager }

    private var mSupplier: DarkPreferenceSupplier? = null

    private val sp = PreferenceManager.getDefaultSharedPreferences(context)

    private var isAutoMode = sp.getBoolean(SP_AUTO_mode, false)

    override fun onStart(owner: LifecycleOwner) {
        mSupplier = (owner as DarkPreferenceSupplier).apply {
            get(DARK_PREFERENCE_START).onPreferenceChangeListener = this@DarkModeSettings
            get(DARK_PREFERENCE_END).onPreferenceChangeListener = this@DarkModeSettings
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        mSupplier = null
    }

    /**
     * Sets the system-wide night mode.
     *
     * Direct call [UiModeManager.setNightMode] requires **MODIFY_DAY_NIGHT_MODE**
     * permission. And that is a __signature|privileged__ permission, can not get easily.
     *
     * Modify secure system settings to bypass that permission,
     * Requires **WRITE_SECURE_SETTINGS**, can provide by the user.
     *
     * @return  false if an error occurred or failed to set mode.
     *
     * @see     UiModeManager.setNightMode
     * */
    @SuppressLint("WrongConstant")
    fun setDarkMode(enabled: Boolean): Boolean {
        val newMode = if (enabled) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO
        val currentMode = mManager.nightMode

        if (currentMode == newMode) {
            Timber.v("Already in %s mode", newMode)
            return true
        }

        Timber.d("Current mode: $currentMode change to $newMode")
        try {
            Settings.Secure.putInt(
                    context.contentResolver,
                    SYSTEM_SECURE_PROP_DARK_MODE,
                    newMode
            )

            // Manually trigger car mode
            // UiManager will call setNightMode() after carMode
            mManager.enableCarMode(0)
            mManager.disableCarMode(0)
            return mManager.nightMode == newMode
        } catch (e: SecurityException) {
            Timber.d(e)
            return false
        }
    }

    /**
     * Switch dark mode **on/off** if current time at the user-selected range.
     *
     * @return  **True** If dark mode adjusted
     *
     * @see     DarkTimeUtil.isInTime
     * */
    private fun adjustModeOnTime(start: LocalTime, end: LocalTime): Boolean {
        val currentMode = isDarkMode() == true
        val isInRange = DarkTimeUtil.isInTime(start, end, LocalTime.now())
        if (isInRange.xor(currentMode)) {
            setDarkMode(isInRange)
        }

        Timber.v("User currently %s mode range", if (isInRange) "in" else "not in")
        return isInRange.xor(currentMode)
    }

    /**
     * Called when user selected new dark mode time, set new switch job alarm
     * */
    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        val key = preference.key
        val time = newValue as LocalTime
        // Set start alarm at tomorrow
        setNextAlarm(time, key)

        // Adjust dark mode if needed
        val startTime = if (key == DARK_PREFERENCE_START) time else getStartTime()
        val endTime = if (key == DARK_PREFERENCE_START) getEndTime() else time
        val adjusted = adjustModeOnTime(startTime, endTime)

        if (AutoDarkApplication.isOnePlus()) {
            // ignore current dark mode on onePlus
            val darkMode = DarkTimeUtil.isInTime(startTime, endTime, LocalTime.now())
            DarkWallpaperHelper.getInstance(context, null).onAlarm(darkMode)
        } else if (adjusted) {
            DarkWallpaperHelper.getInstance(context, null).onAlarm(isDarkMode() == true)
        }
        return true
    }

    /**
     * Returns a pending alarm for trigger dark mode at future
     *
     * @param   time Time in milliseconds for set alarm
     * @param   type Turn the dark mode *ON/OFF*.  Either [DARK_PREFERENCE_START]
     *          or [DARK_PREFERENCE_END].
     *
     *          If same type intent created, the old one will be replaced.
     *
     * @see     PendingIntent.getBroadcast
     * */
    private fun pendingDarkAlarm(time: Long, @DarkPreferenceType type: String): PendingIntent {
        val intent = Intent(context, DarkModeAlarmReceiver::class.java)
        intent.putExtra(PARAM_ALARM_TYPE, type)
        intent.putExtra(PARAM_ALARM_TIME, time)

        return PendingIntent.getBroadcast(
                context,
                if (type == DARK_PREFERENCE_START) REQUEST_ALARM_START else REQUEST_ALARM_END,
                intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
        )
    }

    /**
     * Set a pending alarm for trigger dark mode at future
     *
     * @param   time Time in milliseconds for set alarm
     * @param   type Turn the dark mode *ON/OFF*.  Either [DARK_PREFERENCE_START]
     *          or [DARK_PREFERENCE_END].
     *
     * @see     pendingDarkAlarm
     * @see     AlarmManager.set
     * @see     DarkModeSettings.onAlarm
     * */
    private fun setNextAlarm(time: LocalTime, @DarkPreferenceType type: String) {
        val alarmTime = getTodayOrNextDay(time)
        mAlarmManager.set(AlarmManager.RTC, alarmTime, pendingDarkAlarm(alarmTime, type))
        Timber.v("Set %s alarm: %s : %s", type, getPersistFormattedString(time), alarmTime)
    }

    fun setAllAlarm(): Boolean = setAllAlarm(getStartTime(), getEndTime())

    /**
     * Pending the start/end alarm for dark mode switch
     *
     * @return  **True** if dark mode has changed
     *
     * @see     getTodayOrNextDay
     * @see     adjustModeOnTime
     * */
    fun setAllAlarm(startTime: LocalTime, endTime: LocalTime): Boolean {
        val isAdjusted = adjustModeOnTime(startTime, endTime)

        setNextAlarm(startTime, DARK_PREFERENCE_START)
        setNextAlarm(endTime, DARK_PREFERENCE_END)
        return isAdjusted
    }

    fun cancelAllAlarm(): Boolean = cancelAllAlarm(getStartTime(), getEndTime())

    /**
     * Cancel all the pending alarm
     *
     * @see     pendingDarkAlarm
     * */
    fun cancelAllAlarm(startTime: LocalTime, endTime: LocalTime): Boolean {

        // deactivate dark mode
        setDarkMode(false)

        // cancel job on next day
        val startMillis = getTodayOrNextDay(startTime)
        val endMillis = getTodayOrNextDay(endTime)

        val startJob = pendingDarkAlarm(startMillis, DARK_PREFERENCE_START)
        val endJob = pendingDarkAlarm(endMillis, DARK_PREFERENCE_END)

        mAlarmManager.cancel(startJob)
        mAlarmManager.cancel(endJob)

        Timber.v("Cancel start job: %s: %s", getPersistFormattedString(startTime), startMillis)
        Timber.v("Cancel end job: %s: %s", getPersistFormattedString(endTime), endMillis)
        return DarkTimeUtil.isInTime(startTime, endTime, LocalTime.now())
    }

    /**
     * Turns auto mode ON/OFF
     *
     * @return  **True** if mode switched successfully
     * */
    @RequiresPermission(allOf = [android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION])
    suspend fun triggerAutoMode(): Boolean {
        if (isAutoMode) {
            isAutoMode = false
            // replace with custom alarm
            setAllAlarm()
            saveAutoMode(false)
            return true
        }

        val locationUtil = DarkLocationUtil.getInstance(context)
        val location = locationUtil.getLastLocation()
        if (location != null) {
            val darkTimeStr = DarkTimeUtil.getDarkTimeString(location)
            Timber.i("Sunrise at ${darkTimeStr.first}, sunset at ${darkTimeStr.second}")
            // save dark time for master switch
            saveAutoTime(darkTimeStr)
            saveAutoMode(true)

            isAutoMode = true
            val darkTime = DarkTimeUtil.getDarkTime(darkTimeStr)
            setAllAlarm(darkTime.second, darkTime.first)
            return true
        }

        Timber.i("Location is unavailable")
        return false
    }

    /**
     * Called when receiving dark mode job at the scheduled time
     * Adjust dark mode now
     *
     * */
    fun onAlarm(intent: Intent) {
        Timber.v("Dark alarm broadcast Received")
        val type = intent.getStringExtra(PARAM_ALARM_TYPE)!!

        val switch = type == DARK_PREFERENCE_START
        val time = intent.getLongExtra(PARAM_ALARM_TIME, -1)
        val nextAlarm = DarkTimeUtil.toNextDayAlarmMillis(time)
        val pendingIntent = pendingDarkAlarm(nextAlarm, type)

        try {
            setDarkMode(switch)

            // pending next alarm if no error occurred
            (context.getSystemService(Activity.ALARM_SERVICE) as AlarmManager)
                    .set(AlarmManager.RTC, nextAlarm, pendingIntent)
            Timber.v("Dark job $type finished, pending next alarm: $nextAlarm")
            // change wallpaper now
            DarkWallpaperHelper.getInstance(context, null).onAlarm(switch)
        } catch (e: Exception) {
            Timber.i(e)
            Toast.makeText(context, R.string.dark_mode_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Active dark mode after boot complete
     * Set force-dark if needed
     *
     * @see     DarkModeSettings.setForceDark
     * @see     DARK_PREFERENCE_FORCE_ROOT
     * */
    fun onBoot() {
        if (sp.getBoolean(DARK_PREFERENCE_FORCE_ROOT, false)) {
            CoroutineScope(Dispatchers.Main).launch {
                // Check set job result
                val result = setForceDark(true)
                Timber.v("Force-dark job %s.", if (result) "Succeed" else "Failed")
            }
        }

        val autoMode = sp.getBoolean(SP_AUTO_mode, false)
        val masterSwitch = sp.getBoolean(SP_KEY_MASTER_SWITCH, false)
        if (!masterSwitch) return

        val startTime = sp.getString(if (autoMode) SP_AUTO_TIME_SUNSET else DARK_PREFERENCE_START, null)
        val endTime = sp.getString(if (autoMode) SP_AUTO_TIME_SUNRISE else DARK_PREFERENCE_END, null)

        Timber.i("onBootBroadcast: Switch $masterSwitch, AutoMode: $autoMode")

        if (startTime == null || endTime == null) {
            Timber.v("No job to do.")
            return
        } else {
            Timber.v("onBootBroadcast: Start $startTime End: $endTime")
        }

        // adjust dark mode if need after boot up
        // renew alarm
        val darkModeChanged = setAllAlarm(
                DarkTimeUtil.getPersistLocalTime(startTime),
                DarkTimeUtil.getPersistLocalTime(endTime)
        )

        if (darkModeChanged) { // Change wallpaper now
            DarkWallpaperHelper.getInstance(context, null).onBoot(isDarkMode() == true)
        }
    }

    fun isAutoMode(): Boolean = isAutoMode

    /**
     * Returns system-wide night mode state
     *
     * @return  **True** if dark mode is currently in use,
     *          *Null** when error happened.
     *
     * @see     UiModeManager.getNightMode
     * */
    fun isDarkMode(): Boolean? {
        val current = mManager.nightMode
        return if (current == -1) null else current != UiModeManager.MODE_NIGHT_NO
    }

    private fun saveAutoTime(timePair: Pair<String, String>) {
        sp.edit().putString(SP_AUTO_TIME_SUNRISE, timePair.first)
                .putString(SP_AUTO_TIME_SUNSET, timePair.second)
                .apply()
    }

    private fun saveAutoMode(isAutoMode: Boolean) {
        sp.edit().putBoolean(SP_AUTO_mode, isAutoMode).apply()
    }

    fun getStartTime(): LocalTime = getPreferenceTime(DARK_PREFERENCE_START)

    fun getEndTime(): LocalTime = getPreferenceTime(DARK_PREFERENCE_END)

    fun overrideIfNeeded(mode: Boolean = false) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            val nightMode = mManager.nightMode
            val override = nightMode != UiModeManager.MODE_NIGHT_NO && nightMode != UiModeManager.MODE_NIGHT_YES
            if (override) {
                setDarkMode(mode)
            }
        }
    }

    private fun getPreferenceTime(type: String): LocalTime {
        requireNotNull(mSupplier) { "Exception call: Preference has been detached." }
        return if (isAutoMode) {
            val darkTimeStr = if (type == DARK_PREFERENCE_START) {
                sp.getString(SP_AUTO_TIME_SUNSET, "19:20")!!
            } else {
                sp.getString(SP_AUTO_TIME_SUNRISE, "06:15")!!
            }
            DarkTimeUtil.getPersistLocalTime(darkTimeStr)
        } else {
            mSupplier!!.get(type).time
        }
    }
}