package me.ranko.autodark.Services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.Tile.*
import android.service.quicksettings.TileService
import me.ranko.autodark.AutoDarkApplication
import me.ranko.autodark.Constant
import me.ranko.autodark.R
import me.ranko.autodark.core.DarkModeSettings
import me.ranko.autodark.ui.PermissionActivity
import me.ranko.autodark.ui.PermissionViewModel
import timber.log.Timber

/**
 * QS Tile Service for OnePlus
 *
 * @author 0ranko0P
 * */
class DarkModeTileService : TileService() {
    private lateinit var mTile: Tile

    private lateinit var darkSettings: DarkModeSettings

    override fun onStartListening() {
        super.onStartListening()
        darkSettings = DarkModeSettings.getInstance(application)
        val isDarkMode = darkSettings.isDarkMode()

        mTile = qsTile
        mTile.icon = Icon.createWithResource(this, R.drawable.ic_dark_theme)
        mTile.label = getString(R.string.tile_dark_theme)
        mTile.state = when (isDarkMode) {
            true -> STATE_ACTIVE

            false -> STATE_INACTIVE

            null -> STATE_UNAVAILABLE
        }
        mTile.updateTile()
    }

    override fun onClick() {
        if (PermissionViewModel.checkSecurePermission(this)) {
            darkSettings.setDarkMode(mTile.state != STATE_ACTIVE)
            mTile.updateTile()
        } else {
            val intent = Intent(this, PermissionActivity::class.java)
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        @JvmStatic
        fun setUp(context: Context) {
            val isOnePlus = AutoDarkApplication.isOnePlus()
            if (isOnePlus) return

            if (AutoDarkApplication.isComponentEnabled(context, DarkModeTileService::class.java)) {
                Timber.d("Not ${Constant.BRAND_ONE_PLUS}, disabling tile service")

                context.packageManager.setComponentEnabledSetting(
                    ComponentName(context.packageName, DarkModeTileService::class.java.name),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    , PackageManager.DONT_KILL_APP
                )
            } else {
                Timber.v("Tile service disabled")
            }
        }
    }
}