package me.ranko.autodark

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import me.ranko.autodark.Utils.DarkTimeUtil
import me.ranko.autodark.core.DARK_PREFERENCE_START
import me.ranko.autodark.core.DarkModeSettings

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
            val intentType = if (type == DARK_PREFERENCE_START) REQUEST_ALARM_START else REQUEST_ALARM_END
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
        val type = intent.getStringExtra(PARAM_ALARM_TYPE)!!
        val time = intent.getLongExtra(PARAM_ALARM_TIME, -1)

        val switch = type == DARK_PREFERENCE_START

        try {
            DarkModeSettings.setDarkMode(context, switch)

            // Pending next day alarm if no error occurred
            val nextAlarmTime = DarkTimeUtil.toNextDayAlarmMillis(time)
            val pendingIntent = newPendingIntent(context, type, nextAlarmTime)

            (context.getSystemService(Activity.ALARM_SERVICE) as AlarmManager)
                .set(AlarmManager.RTC, nextAlarmTime, pendingIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, R.string.dark_mode_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }
}
