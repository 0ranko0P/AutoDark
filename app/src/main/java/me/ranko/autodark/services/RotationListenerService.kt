package me.ranko.autodark.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.*
import me.ranko.autodark.R
import me.ranko.autodark.core.WallpaperSetterBinder
import me.ranko.autodark.core.WallpaperSetterBinder.WallpaperSetterServiceCallback
import me.ranko.autodark.core.WallpaperSetterConnection

/**
 * Service that listens for orientation changes.
 *
 * Some modified Android skins cropped wallpaper based on current screen orientation,
 * applying wallpaper when the device is in the right rotation [Surface.ROTATION_0].
 * */
class RotationListenerService: Service() {

    private val mBinder = ListenerBinder()

    private var sensorListener: OrientationListener? = null

    private class OrientationListener(
        context: Application,
        var callback: WallpaperSetterServiceCallback?
    ) : OrientationEventListener(context) {

        @Suppress("DEPRECATION")
        private val mDisplay =
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay

        override fun onOrientationChanged(orientation: Int) {
            if (mDisplay.rotation == Surface.ROTATION_0) {
                callback!!.onReadyToSet()
                disable()
            }
        }

        override fun enable() {
            if (canDetectOrientation()) {
                super.enable()
            } else {
                // Address it as cancelled, so helper won't disable next wallpaper alarm
                callback!!.onServiceFailure(CancellationException("Orientation sensor not available, abort."))
                disable()
            }
        }

        override fun disable(){
            super.disable()
            callback = null
        }
    }

    private inner class ListenerBinder : WallpaperSetterBinder() {
        private var callback: WallpaperSetterServiceCallback? = null

        override fun start(callback: WallpaperSetterServiceCallback) {
            this.callback = callback
            sensorListener = OrientationListener(application, callback)
            sensorListener!!.enable()
        }

        override fun destroy() {
            sensorListener = null
            stopForeground(true)
            stopSelf()
        }
    }

    override fun onCreate() {
        val mManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            ROTATION_SERVICE_CHANNEL,
            getString(R.string.service_rotation_name),
            NotificationManager.IMPORTANCE_LOW
        )
        mManager.createNotificationChannel(channel)

        val builder = Notification.Builder(this, ROTATION_SERVICE_CHANNEL)
        builder.setSmallIcon(R.drawable.ic_auto_dark)
        builder.setContentTitle(channel.name)
        builder.setContentText(getString(R.string.service_rotation_listening))
        startForeground(ROTATION_SERVICE_ID, builder.build())
    }

    override fun onBind(intent: Intent?): IBinder = mBinder

    companion object {
        private const val ROTATION_SERVICE_CHANNEL = "ROTATION"
        private const val ROTATION_SERVICE_ID = 12

        fun startForegroundService(context: Context, connection: WallpaperSetterConnection) {
            val intent = Intent(context, RotationListenerService::class.java)
            context.bindService(intent, connection, BIND_AUTO_CREATE)
            context.startForegroundService(intent)
        }
    }
}