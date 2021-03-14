package me.ranko.autodark.Services

import android.app.*
import android.app.PendingIntent.FLAG_ONE_SHOT
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Binder
import android.os.IBinder
import com.android.wallpaper.model.LiveWallpaperInfo
import com.android.wallpaper.module.WallpaperPersister
import com.android.wallpaper.module.WallpaperSetter
import kotlinx.coroutines.*
import me.ranko.autodark.R
import me.ranko.autodark.ui.DarkWallpaperPickerActivity
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeoutException

class DarkLiveWallpaperService : Service() {

    companion object {

        const val ARG_TARGET_WALLPAPER = "arg_w"
        const val ACTION_STOP_SERVICE = "arg_stop"

        private const val LIVE_WALLPAPER_CHANNEL = "LIVE_WALLPAPER"
        private const val LIVE_WALLPAPER_ID = 7

        // up to 30s
        private const val TIMER_MAX_COUNT_DOWN = 30
        private const val TIMER_SLEEP_DURATION = 1000L

        fun buildErrorNotification(context: Context, e: Exception?): Notification = with(context) {
            return@with if (e is TimeoutException) {
                getErrorNotification(this, getString(R.string.service_wallpaper_failed_title), getString(R.string.service_wallpaper_failed_timeout))
            } else {
                val error = getString(R.string.service_wallpaper_failed_error, e?.message ?: "null")
                getErrorNotification(this, getString(R.string.service_wallpaper_failed_error), error)
            }
        }

        private fun getErrorNotification(context: Context, title: String, content: String): Notification {
            val intent = Intent(context, DarkWallpaperPickerActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(context, -1, intent, -1)
            val builder = Notification.Builder(context, LIVE_WALLPAPER_CHANNEL)
            builder.setSmallIcon(R.drawable.ic_auto_dark)
            builder.setContentTitle(title)
            builder.setContentText(content)
            builder.setContentIntent(pendingIntent)
            return builder.build()
        }

        fun createChannel(context: Context): NotificationChannel = with(context) {
            val channelName = getString(R.string.service_wallpaper_channel_name, getString(R.string.chooser_category_live_wallpaper))
            return NotificationChannel(LIVE_WALLPAPER_CHANNEL, channelName, NotificationManager.IMPORTANCE_LOW)
        }
    }

    inner class DarkBinder : Binder(), Shizuku.OnBinderReceivedListener, WallpaperPersister.SetWallpaperCallback {
        var mCallback: WallpaperPersister.SetWallpaperCallback? = null

        fun start(callback: WallpaperPersister.SetWallpaperCallback) {
            mCallback = callback
            Shizuku.addBinderReceivedListener(this)
        }

        override fun onBinderReceived() {
            timer?.cancel()
            unBindShizuku()
            updateNotification(content = getString(R.string.service_wallpaper_setting))
            val setter = WallpaperSetter(WallpaperPersister(applicationContext))
            setter.setCurrentLiveWallpaper(target, mCallback)
            stop()
        }

        override fun onSuccess(id: String) {
            mCallback?.onSuccess(id)
            stop()
        }

        override fun onError(e: Exception?) {
            mCallback?.onError(e)
            if (e is CancellationException) {
                stop()
            } else {
                mManager.notify(LIVE_WALLPAPER_ID, buildErrorNotification(applicationContext, e))
                GlobalScope.launch(Dispatchers.Main) {
                    delay(8000L)
                    stop()
                }
            }
        }
    }

    private lateinit var mManager: NotificationManager
    private lateinit var target: LiveWallpaperInfo
    private lateinit var channel: NotificationChannel

    private lateinit var mTitle: String
    private val mBinder = DarkBinder()

    private var timer: Job? = null

    override fun onCreate() {
        super.onCreate()
        mManager = getSystemService(NotificationManager::class.java)
        channel = createChannel(this)
        mManager.createNotificationChannel(channel)
        // update new title while LiveWallpaperInfo arrived
        val title = channel.name.toString()
        startForeground(LIVE_WALLPAPER_ID, getNotification(title, getString(R.string.service_wallpaper_waiting)))
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.action == ACTION_STOP_SERVICE) {
            timer?.cancel("User cancel")
            onCancel()
        } else {
            target = intent.getParcelableExtra(ARG_TARGET_WALLPAPER)!!
            mTitle = getString(R.string.service_wallpaper_title, channel.name, target.getTitle(baseContext))
            updateNotification(mTitle, getString(R.string.service_wallpaper_waiting))

            timer = countDown()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder = mBinder

    private fun countDown(): Job =  GlobalScope.launch(Dispatchers.Main) {
        val timeStr = getString(R.string.service_wallpaper_waiting)
        var current = 0

        while (isActive) {
            delay(TIMER_SLEEP_DURATION)
            current++
            updateNotification(mTitle, getString(R.string.service_wallpaper_waiting_count_down, timeStr, (TIMER_MAX_COUNT_DOWN - current).toString()))
            if (isActive.not()) break
            if (current >= TIMER_MAX_COUNT_DOWN) {
                timer = null
                unBindShizuku()
                mBinder.onError(TimeoutException("Time out waiting Shizuku online"))
                break
            }
        }
    }

    private fun onCancel() {
        unBindShizuku()
        mBinder.onError(CancellationException())
    }

    private fun updateNotification(title: String = mTitle, content: String) {
        mManager.notify(LIVE_WALLPAPER_ID, getNotification(title, content))
    }

    private fun getNotification(title: String, content: String): Notification {
        val stopIntent = Intent(this, javaClass)
        stopIntent.action = ACTION_STOP_SERVICE
        val pendingIntent = PendingIntent.getService(this, 0, stopIntent, FLAG_ONE_SHOT)

        val cancelAction = Notification.Action.Builder(
                Icon.createWithResource(applicationContext, R.drawable.ic_auto_dark),
                getString(android.R.string.cancel),
                pendingIntent).build()

        val builder = Notification.Builder(this, LIVE_WALLPAPER_CHANNEL)
        builder.setSmallIcon(R.drawable.ic_auto_dark)
        builder.setContentTitle(title)
        builder.setContentText(content)
        builder.addAction(cancelAction)
        return builder.build()
    }

    private fun unBindShizuku() = Shizuku.removeBinderReceivedListener(mBinder)

    fun stop() {
        mBinder.mCallback = null
        stopForeground(false)
        stopSelf()
    }
}