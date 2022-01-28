package me.ranko.autodark.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.drawable.Icon
import android.os.IBinder
import com.android.wallpaper.model.LiveWallpaperInfo
import kotlinx.coroutines.CancellationException
import me.ranko.autodark.R
import me.ranko.autodark.core.ShizukuApi
import me.ranko.autodark.core.ShizukuStatus
import me.ranko.autodark.core.WallpaperSetterBinder
import me.ranko.autodark.core.WallpaperSetterBinder.WallpaperSetterServiceCallback
import rikka.shizuku.Shizuku

class DarkLiveWallpaperService : Service() {

    companion object {

        const val ARG_TARGET_WALLPAPER = "arg_w"
        const val ACTION_STOP_SERVICE = "arg_stop"

        private const val LIVE_WALLPAPER_CHANNEL = "LIVE_WALLPAPER"
        private const val LIVE_WALLPAPER_ID = 7

        fun startForegroundService(context: Context, wallpaper: LiveWallpaperInfo, conn: ServiceConnection) {
            val intent = Intent(context, DarkLiveWallpaperService::class.java)
            intent.putExtra(ARG_TARGET_WALLPAPER, wallpaper)
            context.bindService(intent, conn, BIND_AUTO_CREATE)
            context.startForegroundService(intent)
        }
    }

    private inner class ShizukuListener(var callback: WallpaperSetterServiceCallback?) :
        Shizuku.OnBinderReceivedListener {

        init {
            Shizuku.addBinderReceivedListenerSticky(this)
        }

        override fun onBinderReceived() {
            val status = ShizukuApi.checkShizukuCompat(application, true)
            if (status == ShizukuStatus.AVAILABLE) {
                updateNotification(content = getString(R.string.service_wallpaper_setting))
                callback?.onReadyToSet()
            } else {
                val err = CancellationException("Failed to connect Shizuku: $status")
                callback?.onServiceFailure(err)
                val builder = Notification.Builder(application, LIVE_WALLPAPER_CHANNEL)
                builder.setSmallIcon(R.drawable.ic_auto_dark)
                builder.setContentTitle(getString(R.string.service_wallpaper_failed_title))
                builder.setContentText(getString(R.string.service_wallpaper_failed_error, status.name))
                mManager.notify(LIVE_WALLPAPER_ID, builder.build())
            }
            unsubscribe()
        }

        fun unsubscribe() {
            Shizuku.removeBinderReceivedListener(this)
            callback = null
        }
    }

    private inner class DarkBinder : WallpaperSetterBinder() {
        private var mCallback: WallpaperSetterServiceCallback? = null
        private var mShizukuListener: ShizukuListener? = null

        override fun start(callback: WallpaperSetterServiceCallback) {
            mCallback = callback
            mShizukuListener = ShizukuListener(callback)
        }

        fun onCancel() {
            val exception = CancellationException("User cancel")
            mShizukuListener?.unsubscribe()
            mCallback!!.onServiceFailure(exception)
        }

        override fun destroy() {
            mCallback = null
            mShizukuListener = null
            stopForeground(true)
            stopSelf()
        }
    }

    private lateinit var mManager: NotificationManager
    private lateinit var target: LiveWallpaperInfo
    private lateinit var channel: NotificationChannel

    private lateinit var mTitle: String
    private val mBinder = DarkBinder()

    override fun onCreate() {
        super.onCreate()
        mManager = getSystemService(NotificationManager::class.java)
        val channelName = getString(R.string.service_wallpaper_channel_name, getString(R.string.chooser_category_live_wallpaper))

        channel = NotificationChannel(LIVE_WALLPAPER_CHANNEL, channelName, NotificationManager.IMPORTANCE_LOW)
        mManager.createNotificationChannel(channel)
        // update new title while LiveWallpaperInfo arrived
        val title = channel.name.toString()
        startForeground(LIVE_WALLPAPER_ID, getNotification(title, getString(R.string.service_wallpaper_waiting)))
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.action == ACTION_STOP_SERVICE) {
            mBinder.onCancel()
        } else {
            target = intent.getParcelableExtra(ARG_TARGET_WALLPAPER)!!
            mTitle = getString(R.string.service_wallpaper_title, channel.name, target.getTitle(baseContext))
            updateNotification(mTitle, getString(R.string.service_wallpaper_waiting))
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder = mBinder

    private fun updateNotification(title: String = mTitle, content: String) {
        mManager.notify(LIVE_WALLPAPER_ID, getNotification(title, content))
    }

    private fun getNotification(title: String, content: String): Notification {
        val stopIntent = Intent(this, javaClass)
        stopIntent.action = ACTION_STOP_SERVICE
        val pendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

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
}