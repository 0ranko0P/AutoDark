package me.ranko.autodark.core

import android.app.Activity
import android.app.AlarmManager
import android.app.UiModeManager
import android.content.Context
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import me.ranko.autodark.BuildConfig
import me.ranko.autodark.Constant
import me.ranko.autodark.Constant.COMMAND_GET_FORCE_DARK
import me.ranko.autodark.Exception.CommandExecuteError
import me.ranko.autodark.Receivers.DarkModeAlarmReceiver
import me.ranko.autodark.Utils.DarkTimeUtil
import me.ranko.autodark.Utils.DarkTimeUtil.getPersistFormattedString
import me.ranko.autodark.Utils.DarkTimeUtil.getTodayOrNextDay
import me.ranko.autodark.Utils.DarkTimeUtil.toAlarmMillis
import me.ranko.autodark.Utils.DarkTimeUtil.toNextDayAlarmMillis
import me.ranko.autodark.Utils.ShellJobUtil
import me.ranko.autodark.ui.Preference.DarkDisplayPreference
import timber.log.Timber
import java.time.LocalTime

const val SYSTEM_SECURE_PROP_DARK_MODE = "ui_night_mode"

// Force-dark mode
// Return null on some device while force-dark is false
const val SYSTEM_PROP_FORCE_DARK = "debug.hwui.force_dark"

const val DARK_PREFERENCE_START = "dark_mode_time_start"
const val DARK_PREFERENCE_END = "dark_mode_time_end"

/**
 * Experimental feature
 * System will reset this flag after reboot
 * */
const val DARK_PREFERENCE_FORCE = "dark_mode_force"
const val DARK_PREFERENCE_FILE_NAME = "${BuildConfig.APPLICATION_ID}_preferences"

interface DarkPreferenceSupplier {
    fun get(type: String): DarkDisplayPreference
}

/**
 *  Dark mode Controller
 *  <p>
 *  Listener on preference changes to set dark mode on/off time
 *
 * @author 0ranko0P
 *
 * @see    DarkDisplayPreference
 * @see    UiModeManager
 * */
class DarkModeSettings(private val context: Context) :
    OnPreferenceChangeListener, DefaultLifecycleObserver {

    private val mAlarmManager: AlarmManager =
        context.getSystemService(Activity.ALARM_SERVICE) as AlarmManager

    private var mSupplier: DarkPreferenceSupplier? = null

    override fun onStart(owner: LifecycleOwner) {
        mSupplier = owner as DarkPreferenceSupplier
    }

    override fun onStop(owner: LifecycleOwner) {
        mSupplier = null
    }

    companion object {

        /**
         * Sets the system-wide night mode.
         * <p>
         * Direct call setNightMode requires <strong>MODIFY_DAY_NIGHT_MODE</strong> permission
         * And that's a <strong>signature|privileged</strong> permission, can not get easily.
         * <p>
         * Modify secure system settings to by pass that permission,
         * Requires WRITE_SECURE_SETTINGS, can provide by user.
         *
         * @return false if error occurred or failed to set mode.
         *
         * @see UiModeManager.setNightMode
         * */
        @JvmStatic
        fun setDarkMode(manager: UiModeManager, context: Context, enabled: Boolean): Boolean {
            val newMode = if (enabled) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO

            if (manager.nightMode == newMode) {
                Timber.v("Already in %s mode", newMode)
                return true
            }

            try {
                Timber.d("Current mode: ${manager.currentModeType} change to $newMode")
                Settings.Secure.putInt(
                    context.contentResolver,
                    SYSTEM_SECURE_PROP_DARK_MODE,
                    newMode
                )

                // Manually trigger car mode
                // UiManager will call setNightMode() after carMode
                manager.enableCarMode(0)
                manager.disableCarMode(0)
                return manager.nightMode == newMode
            } catch (e: SecurityException) {
                Timber.e(e.localizedMessage)
                return false
            }
        }

        @JvmStatic
        fun setDarkMode(context: Context, enabled: Boolean) {
            val manager = ContextCompat.getSystemService(context, UiModeManager::class.java)!!
            setDarkMode(manager, context, enabled)
        }

        /**
         * Configure system force-dark mode
         *
         * @return  false If failed to set value
         *
         * @see     SYSTEM_PROP_FORCE_DARK
         * @see     Constant.COMMAND_SET_FORCE_DARK_ON
         * */
        suspend fun setForceDark(enabled: Boolean): Boolean {
            try {
                // Run: set force mode && get force mode
                val setCMD = if (enabled) Constant.COMMAND_SET_FORCE_DARK_ON else Constant.COMMAND_SET_FORCE_DARK_OFF
                val command = "$setCMD && su -c $COMMAND_GET_FORCE_DARK"

                val nowStatus = ShellJobUtil.runSudoJobForValue(command)!!.trim().toBoolean()
                return nowStatus == enabled
            } catch (e: Exception) {
                Timber.e("Error: ${e.localizedMessage}")
                return false
            }
        }

        /**
         * @return  current force-dark mode status
         *
         * @throws  CommandExecuteError permission denied when
         *          do not have root access.
         *
         * @see     COMMAND_GET_FORCE_DARK
         * */
        @Throws(CommandExecuteError::class)
        suspend fun getForceDark(): Boolean {
            val forceDark = ShellJobUtil.runJobForValue(COMMAND_GET_FORCE_DARK)
            if (forceDark == null || forceDark.trim().isEmpty()) {
                Timber.v("Force-dark settings is untouched")
                return false
            } else {
                return forceDark.trim().toBoolean()
            }
        }

        /**
         * Adjust mode on/off now if current time at user selected range.
         *
         * @return  true if is in range now and dark mode is active
         *
         * @see     DarkTimeUtil.isInTime
         * */
        @JvmStatic
        fun adjustModeOnTime(context: Context, start: LocalTime, end: LocalTime): Boolean {
            val shouldActive = DarkTimeUtil.isInTime(start, end, LocalTime.now())

            setDarkMode(context, shouldActive)
            Timber.d("User currently ${if (shouldActive) "in" else "not in"} mode range")
            return shouldActive
        }

        /**
         * @see UiModeManager.getNightMode
         * */
        @JvmStatic
        fun isDarkModeOn(context: Context): Boolean {
            val manager = ContextCompat.getSystemService(context, UiModeManager::class.java)!!
            return manager.nightMode == UiModeManager.MODE_NIGHT_YES
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        val key = preference.key
        if (key != DARK_PREFERENCE_START && key != DARK_PREFERENCE_END)
            throw RuntimeException("Wtf are you listening? $key")

        val time = newValue as LocalTime
        var timeMillis = toAlarmMillis(time)

        // Set start alarm at tomorrow
        val isNextDay = DarkTimeUtil.isNextDay(LocalTime.now(), time)
        if (isNextDay) {
            timeMillis = toNextDayAlarmMillis(timeMillis)
        }

        pendingNextAlarm(timeMillis, key)

        // Active dark mode now if needed to let user see the effect
        if (key == DARK_PREFERENCE_START) {
            adjustModeOnTime(time, getEndTime())
        } else {
            adjustModeOnTime(getStartTime(), time)
        }

        Timber.d(
            "Pending alarm at ${if (isNextDay) "NextDay" else ""} %s, epoch: %s",
            getPersistFormattedString(time), timeMillis
        )
        return true
    }


    private fun adjustModeOnTime(start: LocalTime, end: LocalTime): Boolean {
        return adjustModeOnTime(context, start, end)
    }

    /**
     * Set alarm to trigger dark mode
     *
     * @param
     *
     * @see DarkModeAlarmReceiver
     * */
    private fun pendingNextAlarm(time: Long, type: String): Long {
        val intent = DarkModeAlarmReceiver.newPendingIntent(context, type, time)
        mAlarmManager.set(AlarmManager.RTC, time, intent)
        Timber.v("Pending %s alarm %s", type, time)
        return time
    }

    fun setAllAlarm() {
        val startTime = getStartTime()
        val endTime = getEndTime()

        adjustModeOnTime(startTime, endTime)

        // check if job already done, cancel job on next day
        val startMillis = getTodayOrNextDay(startTime)
        val endMillis = getTodayOrNextDay(endTime)

        pendingNextAlarm(startMillis, DARK_PREFERENCE_START)
        pendingNextAlarm(endMillis, DARK_PREFERENCE_END)

        Timber.v("Set start job: %s: %s", getPersistFormattedString(startTime), startMillis)
        Timber.v("Set end job: %s: %s", getPersistFormattedString(endTime), endMillis)
    }

    fun cancelAllAlarm() {
        val startTime = getStartTime()
        val endTime = getEndTime()

        // DeActive dark mode now
        setDarkMode(context, false)

        // check if job already done, cancel job on next day
        val startMillis = getTodayOrNextDay(startTime)
        val endMillis = getTodayOrNextDay(endTime)

        val startJob = DarkModeAlarmReceiver.newPendingIntent(
            context,
            DARK_PREFERENCE_START,
            startMillis
        )

        val endJob = DarkModeAlarmReceiver.newPendingIntent(
            context,
            DARK_PREFERENCE_END,
            endMillis
        )

        mAlarmManager.cancel(startJob)
        mAlarmManager.cancel(endJob)

        Timber.v("Cancel start job: %s: %s", getPersistFormattedString(startTime), startMillis)
        Timber.v("Cancel end job: %s: %s", getPersistFormattedString(endTime), endMillis)
    }

    fun getStartTime(): LocalTime = getPreferenceTime(DARK_PREFERENCE_START)

    fun getEndTime(): LocalTime = getPreferenceTime(DARK_PREFERENCE_END)

    private fun getPreferenceTime(type: String): LocalTime {
        requireNotNull(mSupplier) { "Exception call: Preference has been detached." }
        return mSupplier!!.get(type).time
    }
}