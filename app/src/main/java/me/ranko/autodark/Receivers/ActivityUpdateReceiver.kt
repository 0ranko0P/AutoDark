package me.ranko.autodark.Receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemProperties
import android.util.Log
import de.robv.android.xposed.XposedBridge
import me.ranko.autodark.BuildConfig
import me.ranko.autodark.Constant
import me.ranko.autodark.Utils.FileUtil

class ActivityUpdateReceiver(private val context: Context) : BroadcastReceiver() {

    companion object {
        private const val TAG = "XCore: Receiver"

        /**
         * Holds blocked apps
         * 
         * @see     ActivityUpdateReceiver.sendNewList
         * */
        @JvmStatic
        private lateinit var mBlockSet: HashSet<String>

        private const val ACTION_NEW_ACTIVITY = "me.ranko0p.intent.action.NEW_ACTIVITY"
        private const val ACTION_RELOAD_LIST = "me.ranko0p.intent.action.RELOAD_LIST"

        const val ACTION_RELOAD_RESULT = "me.ranko0p.intent.action.RELOAD_RESULT"

        /**
         * Debug only
         * */
        const val ACTION_SERVER_PRINT_LIST = "me.ranko0p.intent.action.SERVER_PRINT"

        private const val EXTRA_KEY_PACKAGE = "k_pkg"

        /**
         * Debug only
         * */
        private const val EXTRA_KEY_TIME_START = "k_start"

        private const val BROADCAST_MAX_SIZE = 64

        /**
         * Size of new block list
         * SystemServer will read from file if list larger than [BROADCAST_MAX_SIZE]
         * */
        private const val EXTRA_KEY_LIST_SIZE = "k_ll"
        private const val EXTRA_KEY_LIST = "k_list"

        /**
         * Report block list update status to AutoDark
         * */
        const val EXTRA_KEY_LIST_RESULT = "k_result"

        const val STATUS_LIST_LOAD_START = 0x001A
        const val STATUS_LIST_LOAD_FAILED = STATUS_LIST_LOAD_START.shl(1)
        const val STATUS_LIST_LOAD_SUCCEED = STATUS_LIST_LOAD_FAILED.shl(1)

        @JvmStatic
        fun register(context: Context): ActivityUpdateReceiver {
            val receiver = ActivityUpdateReceiver(context)
            context.registerReceiver(receiver, IntentFilter(ACTION_NEW_ACTIVITY))

            val filter = IntentFilter(ACTION_RELOAD_LIST)
            filter.addAction(ACTION_SERVER_PRINT_LIST)
            context.registerReceiver(receiver, filter, Constant.PERMISSION_DARK_BROADCAST, null)
            return receiver
        }

        fun sendNewActivity(context: Context, pkgName: String) {
            val intent = Intent(ACTION_NEW_ACTIVITY)
            intent.putExtra(EXTRA_KEY_TIME_START, System.currentTimeMillis())
            intent.putExtra(EXTRA_KEY_PACKAGE, pkgName)
            context.sendBroadcast(intent)
        }

        fun sendNewList(context: Context, blockList: ArrayList<String>) {
            val intent = Intent(ACTION_RELOAD_LIST)
            intent.putExtra(EXTRA_KEY_TIME_START, System.currentTimeMillis())
            intent.putExtra(EXTRA_KEY_LIST_SIZE, blockList.size)
            intent.setPackage(Constant.ANDROID_PACKAGE)
            if (blockList.size <= BROADCAST_MAX_SIZE) {
                intent.putStringArrayListExtra(EXTRA_KEY_LIST, blockList)
            }
            context.sendBroadcast(intent)
        }

        private fun isForceDark() = SystemProperties.getBoolean(Constant.SYSTEM_PROP_FORCE_DARK, false)
    }

    init {
        val time = System.currentTimeMillis()
        val list = FileUtil.readList(Constant.BLOCK_LIST_PATH)
        mBlockSet =  if (list == null) HashSet() else HashSet(list)
        XposedBridge.log("onInit: Load block list: ${mBlockSet.size}, time: ${System.currentTimeMillis() - time}ms")
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (BuildConfig.DEBUG) Log.d(TAG, "onReceive: action: ${intent.action}")

        when (intent.action) {
            ACTION_NEW_ACTIVITY -> { // User opened a new activity

                val start = intent.getLongExtra(EXTRA_KEY_TIME_START, -1)
                val pkg = intent.getStringExtra(EXTRA_KEY_PACKAGE) ?: ""

                val force: Boolean = isForceDark()
                val block = mBlockSet.contains(pkg)

                // ForceDark ON  == in list --> ForceDark OFF
                // ForceDark OFF == not in list --> ForceDark ON
                if (block == force) {
                    SystemProperties.set(Constant.SYSTEM_PROP_FORCE_DARK, force.not().toString())
                    Log.v(TAG, "onNewAct: Found target: $pkg switching ForceDark from $force to ${isForceDark()}, time cost: ${System.currentTimeMillis() - start}ms")
                }
            }

            ACTION_RELOAD_LIST -> { // Request Block list update
                if (!updateLoadProgress(STATUS_LIST_LOAD_START)) return

                if (!Constant.BLOCK_LIST_PATH.toFile().exists()) {
                    XposedBridge.log("onReload: Block list file not readable: ${Constant.BLOCK_LIST_PATH.toFile().absolutePath}")
                    updateLoadProgress(STATUS_LIST_LOAD_FAILED)
                    return
                }
                val start = intent.getLongExtra(EXTRA_KEY_TIME_START, -1)
                val size = intent.getIntExtra(EXTRA_KEY_LIST_SIZE, -1)
                Log.d(TAG, "onReload: size: $size, largeList: ${size > BROADCAST_MAX_SIZE}")

                val newList: ArrayList<String>? = if (size <= BROADCAST_MAX_SIZE) {
                    intent.getStringArrayListExtra(EXTRA_KEY_LIST)
                } else {
                    FileUtil.readList(Constant.BLOCK_LIST_PATH, size)
                }

                if (newList == null) {
                    XposedBridge.log("onReload: unable to read block list!")
                    updateLoadProgress(STATUS_LIST_LOAD_FAILED)
                } else {
                    mBlockSet.clear()
                    if (newList.isNotEmpty()) mBlockSet.addAll(newList)
                    updateLoadProgress(STATUS_LIST_LOAD_SUCCEED)
                }

                Log.v(TAG, "onReload: Total time cost: ${System.currentTimeMillis() - start}ms")
            }

            ACTION_SERVER_PRINT_LIST -> { // Debug only
                val sb = StringBuffer()
                mBlockSet.forEach {
                    sb.append('[').append(it).append(']').append(',')
                }
                if (sb.isNotEmpty()) sb.deleteCharAt(sb.length - 1)
                Log.e(TAG, "printSet: size: ${mBlockSet.size} $sb")
            }
        }
    }

    private fun updateLoadProgress(status: Int): Boolean {
        val intent = Intent(ACTION_RELOAD_RESULT)
        intent.addFlags(Intent.FLAG_EXCLUDE_STOPPED_PACKAGES)
        intent.putExtra(EXTRA_KEY_LIST_RESULT, status)
        intent.setPackage(BuildConfig.APPLICATION_ID)
        return try {
            context.sendBroadcast(intent)
            true
        } catch (e: Exception) {
            XposedBridge.log(e)
            false
        }
    }

    fun destroy() {
        context.unregisterReceiver(this)
    }
}