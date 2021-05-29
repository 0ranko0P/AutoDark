package me.ranko.autodark.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.ranko.autodark.Constant
import me.ranko.autodark.Utils.FileUtil
import me.ranko.autodark.core.DarkModeSettings
import java.nio.file.Files

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
            if (intent.action.equals(Intent.ACTION_BOOT_COMPLETED)) {
                onBoot()
            } else {
                onAlarm(intent)
            }
        }

        if (Files.exists(Constant.BLOCK_LIST_PATH)) {
            CoroutineScope(Dispatchers.IO).launch {
                val list = FileUtil.readList(Constant.BLOCK_LIST_PATH)
                if (list != null && list.isNotEmpty()) {
                    BlockListReceiver.sendNewList(context, list as ArrayList)
                }
            }
        }
    }
}
