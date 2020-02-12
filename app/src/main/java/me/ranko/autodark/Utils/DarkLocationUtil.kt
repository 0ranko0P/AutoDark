package me.ranko.autodark.Utils

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.annotation.RequiresPermission
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Simple location util
 *
 * @author  0ranko0p
 * */
class DarkLocationUtil private constructor(context: Context) {

    private var mManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val mListener = object : LocationListener {
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {

        }

        override fun onProviderEnabled(provider: String?) {
        }

        override fun onProviderDisabled(provider: String?) {

        }

        override fun onLocationChanged(location: Location?) {

        }
    }

    companion object {
        @Volatile
        private var INSTANCE: DarkLocationUtil? = null

        @JvmStatic
        fun getInstance(context: Context): DarkLocationUtil {
            if (INSTANCE == null) {
                synchronized(DarkLocationUtil::class.java) {
                    if (INSTANCE == null) INSTANCE = DarkLocationUtil(context)
                }
            }
            return INSTANCE!!
        }
    }

    fun isEnabled(): Boolean = LocationManagerCompat.isLocationEnabled(mManager)

    /**
     * Returns best last know location. Will request update for all available location
     * providers if last know location is unknown
     *
     * @return  last know location, **Nullable**
     * */
    @RequiresPermission(allOf = [android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION])
    suspend fun getLastLocation(): Location? {
        if (!isEnabled()) return null

        val location = getBestLastLocation()
        if (location == null) {
            // no last know location, update now
            Timber.d("Lastknowlocation is unavailable, requesting update")
            updateLocation()
            return getBestLastLocation()
        } else {
            return location
        }
    }

    @RequiresPermission(allOf = [android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun getBestLastLocation(): Location? {
        var location: Location? = null
        for (provider in mManager.getProviders(true)) {
            mManager.getLastKnownLocation(provider)?.run {
                if (location == null || accuracy < location!!.accuracy)
                    location = this
            }
        }
        return location
    }

    /**
     * Request location update for all available providers
     *
     * @see     LocationManager.getProviders
     * @see     LocationManager.requestLocationUpdates
     * */
    private suspend fun updateLocation() {
        mManager.getProviders(true).forEach {
            mManager.requestLocationUpdates(it, 50L, 0f, mListener)
        }
        delay(3000L)
        mManager.removeUpdates(mListener)
    }
}