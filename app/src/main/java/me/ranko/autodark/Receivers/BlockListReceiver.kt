package me.ranko.autodark.Receivers

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import de.robv.android.xposed.XposedBridge
import me.ranko.autodark.BuildConfig
import me.ranko.autodark.Constant
import me.ranko.autodark.core.LoadStatus
import me.ranko.autodark.xposed.ATMHooker
import java.lang.ref.WeakReference

/**
 * Broadcast receiver registered in SystemServer that communicates with AutoDark
 * */
class BlockListReceiver private constructor(context: Context, private val hooker: ATMHooker): BroadcastReceiver() {

    companion object {
        private const val TAG = "ATMHooker"

        private const val ACTION_ALIVE_ACK = "me.ranko0p.intent.action.ack"
        const val ACTION_ALIVE = "me.ranko0p.intent.action.alive"

        private const val ACTION_UPDATE_LIST = "me.ranko0p.intent.action.UPDATE_LIST"

        /**
         * Broadcast Action: Report progress of [ACTION_UPDATE_LIST] to AutoDark
         *
         * @see EXTRA_KEY_LIST_PROGRESS
         * */
        const val ACTION_UPDATE_PROGRESS = "me.ranko0p.intent.action.UPDATE_PROGRESS"

        private const val EXTRA_KEY_TIME_START = "k_start"

        private const val EXTRA_KEY_LIST = "k_list"

        /**
         * Report block list update progress, the value must is [LoadStatus]
         * */
        const val EXTRA_KEY_LIST_PROGRESS = "k_result"

        fun register(context: Context, hooker: ATMHooker) {
            val receiver = BlockListReceiver(context, hooker)
            val filter = IntentFilter(ACTION_UPDATE_LIST)
            filter.addAction(ACTION_ALIVE_ACK)
            filter.addAction(Intent.ACTION_SHUTDOWN)
            context.registerReceiver(receiver, filter, Constant.PERMISSION_SEND_DARK_BROADCAST, null)
        }

        fun sendNewList(context: Context, blockList: ArrayList<String>) {
            val intent = Intent(ACTION_UPDATE_LIST)
            intent.putExtra(EXTRA_KEY_TIME_START, System.currentTimeMillis())
            intent.setPackage(Constant.ANDROID_PACKAGE)
            intent.putStringArrayListExtra(EXTRA_KEY_LIST, blockList)
            context.sendBroadcast(intent, Constant.PERMISSION_RECEIVE_DARK_BROADCAST)
        }

        fun sendIsAliveBroadcast(context: Context) {
            val intent = Intent(ACTION_ALIVE_ACK)
            intent.setPackage(Constant.ANDROID_PACKAGE)
            context.sendBroadcast(intent, Constant.PERMISSION_RECEIVE_DARK_BROADCAST)
        }
    }

    private val mContext = WeakReference(context)

    @SuppressLint("LogNotTimber")
    override fun onReceive(context: Context, intent: Intent) {
        if (BuildConfig.DEBUG) Log.d(TAG, "onReceive: action: ${intent.action}")
        when (intent.action) {
            ACTION_UPDATE_LIST -> {
                if (updateLoadProgress(context, LoadStatus.START).not()) return

                val start = intent.getLongExtra(EXTRA_KEY_TIME_START, -1)
                val newList: List<String>? = intent.getStringArrayListExtra(EXTRA_KEY_LIST)
                if (newList == null) {
                    Log.e(TAG, "onReload: block list is null")
                    updateLoadProgress(context, LoadStatus.FAILED)
                } else {
                    hooker.updateList(newList, context)
                    updateLoadProgress(context, LoadStatus.SUCCEED)
                }

                Log.i(TAG, "onReload: Total time cost: ${System.currentTimeMillis() - start}ms")
                if (BuildConfig.DEBUG) hooker.printBlockList()
            }

            ACTION_ALIVE_ACK -> sendAliveBroadcast(context)

            Intent.ACTION_SHUTDOWN -> destroy()
        }
    }

    private fun updateLoadProgress(context: Context, @LoadStatus status: Int): Boolean {
        val intent = Intent(ACTION_UPDATE_PROGRESS)
        intent.addFlags(Intent.FLAG_EXCLUDE_STOPPED_PACKAGES)
        intent.putExtra(EXTRA_KEY_LIST_PROGRESS, status)
        intent.setPackage(BuildConfig.APPLICATION_ID)
        return try {
            context.sendBroadcast(intent, Constant.PERMISSION_RECEIVE_DARK_BROADCAST)
            true
        } catch (e: Exception) {
            XposedBridge.log(e)
            false
        }
    }

    private fun sendAliveBroadcast(context: Context) {
        val intent = Intent(ACTION_ALIVE)
        intent.addFlags(Intent.FLAG_EXCLUDE_STOPPED_PACKAGES)
        intent.setPackage(BuildConfig.APPLICATION_ID)
        try {
            context.sendBroadcast(intent, Constant.PERMISSION_RECEIVE_DARK_BROADCAST)
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
    }

    private fun destroy() {
        mContext.get()?.unregisterReceiver(this)
        mContext.clear()
    }
}