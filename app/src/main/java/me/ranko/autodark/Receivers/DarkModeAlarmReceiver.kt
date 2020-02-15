package me.ranko.autodark.Receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.ranko.autodark.core.DarkModeSettings

/**
 * Receive dark mode job at scheduled time
 *
 * Control logic is in [DarkModeSettings]
 *
 * @see     DarkModeSettings.onBoot
 * @see     DarkModeSettings.onAlarm
 *
 * @author 0ranko0P
 * */
class DarkModeAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        DarkModeSettings.getInstance(context).run {
            if (intent.action.equals(Intent.ACTION_BOOT_COMPLETED))
                onBoot()
            else
                onAlarm(intent)
        }
    }
}
