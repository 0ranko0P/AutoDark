package me.ranko.autodark.Receivers

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ranko.autodark.R
import me.ranko.autodark.Utils.DarkTimeUtil
import me.ranko.autodark.core.*
import me.ranko.autodark.core.DarkModeSettings.Companion.setForceDark
import me.ranko.autodark.ui.MainViewModel
import timber.log.Timber

private const val PARAM_ALARM_TYPE = "ALARM_TYPE"
private const val PARAM_ALARM_TIME = "ALARM_TIME"

private const val REQUEST_ALARM_START = 0x00B0
private const val REQUEST_ALARM_END = REQUEST_ALARM_START.shl(1)

/**
 * Control dark mode on/off at scheduled time
 *
 * @author 0ranko0P
 *
 * @see    DarkModeSettings.onPreferenceChange
 * @see    DarkModeSettings.setAllAlarm
 * @see    DarkModeSettings.cancelAllAlarm
 * */
class DarkModeAlarmReceiver : BroadcastReceiver() {

    companion object {

        /**
         * Create dark mode pending intent for receiver to receive
         * at future to set dark mode on/off
         *
         * @param   type If same type intent created, the old one will be replaced
         * @param   time Time in milliseconds for set alarm
         *
         * @see     PendingIntent.getBroadcast
         * @see     DarkModeAlarmReceiver.onReceive
         * @see     AlarmManager.set
         * */
        @JvmStatic
        fun newPendingIntent(context: Context, type: String, time: Long): PendingIntent {
            val intentType =
                if (type == DARK_PREFERENCE_START) REQUEST_ALARM_START else REQUEST_ALARM_END
            val intent = Intent(context, DarkModeAlarmReceiver::class.java)
            intent.putExtra(PARAM_ALARM_TYPE, type)
            intent.putExtra(PARAM_ALARM_TIME, time)

            return PendingIntent.getBroadcast(
                context,
                intentType,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Execute boot jobs
        if (intent.action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            Timber.v("Received boot broadcast")
            onBoot(context)
            return
        } else {
            Timber.v("Received alarm broadcast")
        }

        val type = intent.getStringExtra(PARAM_ALARM_TYPE)!!
        val time = intent.getLongExtra(PARAM_ALARM_TIME, -1)

        val switch = type == DARK_PREFERENCE_START

        try {
            DarkModeSettings.setDarkMode(context, switch)

            // Pending next day alarm if no error occurred
            val nextAlarmTime = DarkTimeUtil.toNextDayAlarmMillis(time)
            pendingNextAlarm(context, type, nextAlarmTime)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, R.string.dark_mode_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun pendingNextAlarm(context: Context, type: String, time: Long) {
        val pendingIntent = newPendingIntent(context, type, time)

        (context.getSystemService(Activity.ALARM_SERVICE) as AlarmManager)
            .set(AlarmManager.RTC, time, pendingIntent)
    }

    /**
     * Active dark mode after boot complete
     * Set force-dark if needed
     *
     * @see     MainViewModel.setForceDark
     * @see     DarkModeSettings
     * @see     DARK_PREFERENCE_FORCE
     * */
    private fun onBoot(context: Context) {
        val sp = context.getSharedPreferences(DARK_PREFERENCE_FILE_NAME, Activity.MODE_PRIVATE)
        val startStr = sp.getString(DARK_PREFERENCE_START, null)
        val endStr = sp.getString(DARK_PREFERENCE_END, null)
        val forceDark = sp.getBoolean(DARK_PREFERENCE_FORCE, false)

        // User never used app, no boot job to do.
        if (startStr == null || endStr == null) {
            Timber.d("No job to do.")
            return
        }

        val start = DarkTimeUtil.getPersistLocalTime(startStr)
        val end = DarkTimeUtil.getPersistLocalTime(endStr)

        // Active dark mode if need after boot
        DarkModeSettings.adjustModeOnTime(context, start, end)

        pendingNextAlarm(context, DARK_PREFERENCE_START, DarkTimeUtil.getTodayOrNextDay(start))
        pendingNextAlarm(context, DARK_PREFERENCE_END, DarkTimeUtil.getTodayOrNextDay(end))

        if (forceDark) {
            CoroutineScope(Dispatchers.Main).launch {
                Timber.d("Start force dark job")
                // Check set job result
                withContext(Dispatchers.Default) {
                    val result = setForceDark(true)
                    if (result)
                        Timber.d("force-dark job succeed.")
                    else
                        Timber.e("Failed to execute force-dark job")
                }
            }
        } else {
            Timber.v("Force-dark is off")
        }
    }
}
