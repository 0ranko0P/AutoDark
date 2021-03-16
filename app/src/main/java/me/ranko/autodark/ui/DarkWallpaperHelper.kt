package me.ranko.autodark.ui

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.ArrayMap
import androidx.annotation.VisibleForTesting
import com.android.wallpaper.model.LiveWallpaperInfo
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.module.WallpaperPersister
import com.android.wallpaper.module.WallpaperPersister.*
import com.android.wallpaper.module.WallpaperSetter
import kotlinx.coroutines.*
import me.ranko.autodark.Services.DarkLiveWallpaperService
import me.ranko.autodark.core.DarkModeSettings
import me.ranko.autodark.core.ShizukuApi
import me.ranko.autodark.core.ShizukuStatus
import me.ranko.autodark.model.*
import me.ranko.autodark.model.PersistableWallpaper.Companion.getWallpaperFile
import me.ranko.autodark.ui.WallpaperType.DARK_HOME
import me.ranko.autodark.ui.WallpaperType.HOME
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeoutException

enum class WallpaperType {
    HOME, LOCK, DARK_HOME, DARK_LOCK;
}

/**
 * Helper class to manage dark wallpaper
 * */
class DarkWallpaperHelper private constructor(context: Context) {

    companion object {
        private const val PREFS_FILE_NAME = "dark_wallpaper"

        private const val KEY_HIDE_SHIZUKU_WARNING = "hideShizuku"
        private const val KEY_LAST_SETTING_SUCCEED = "NoErr"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: DarkWallpaperHelper? = null

        @JvmStatic
        fun getInstance(context: Context, viewModel: DarkWallpaperPickerViewModel?): DarkWallpaperHelper {
            if (INSTANCE == null) {
                synchronized(DarkWallpaperHelper::class.java) {
                    if (INSTANCE == null) INSTANCE = DarkWallpaperHelper(context.applicationContext)
                }
            }
            if (viewModel != null) {
                INSTANCE!!.viewModelCallback = viewModel
            }
            return INSTANCE!!
        }

        private fun isDarkWallpaperInUse(old: DarkWallpaperInfo, newWallpapers: List<WallpaperInfo>): Boolean {
            for (wallpaper in newWallpapers) {
                if (wallpaper.wallpaperId == old.wallpaperId) return true
            }
            return false
        }

        private class DarkWallpaperConnection(val callback: SetWallpaperCallback): ServiceConnection {

            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                (service as DarkLiveWallpaperService.DarkBinder).start(callback)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                // no-op
            }
        }
    }

    inner class DefaultWallpaperSetterCallback : SetWallpaperCallback {

        override fun onSuccess(id: String) {
            Timber.d("Set wallpaper succeed, new id: %s.", id)
            mPreference.edit().putBoolean(KEY_LAST_SETTING_SUCCEED, true).apply()
            viewModelCallback?.onSuccess(id)
            destroy()
        }

        override fun onError(e: Exception?) {
            if (e is TimeoutException || e is CancellationException) {
                Timber.d(e.localizedMessage)
            } else {
                mPreference.edit().putBoolean(KEY_LAST_SETTING_SUCCEED, false).apply()
                if (viewModelCallback == null) super.onError(e)
            }
            viewModelCallback?.onError(e)
            destroy()
        }

        private fun destroy() {
            if (isApplyingLiveWallpaper()) {
                mContext!!.unbindService(connection!!)
                connection = null
            }

            if (viewModelCallback == null) this@DarkWallpaperHelper.destroy()
        }
    }

    private var mContext: Context? = context.applicationContext

    private val mPreference = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    private val mManager by lazy { WallpaperManager.getInstance(mContext!!) }

    private val mSetter by lazy(LazyThreadSafetyMode.NONE) { WallpaperSetter(WallpaperPersister(mContext)) }

    /**
     * Persisted wallpapers, only contain two types, either [DarkWallpaperInfo] or [LiveWallpaperInfo]
     * */
    private var mPersisted: Array<WallpaperInfo>? = null

    /**
     * Temporary store picked wallpaper, must be following types: [DarkWallpaperInfo], [LiveWallpaperInfo],
     * [CroppedWallpaperInfo] and [SystemWallpaperInfo]
     * */
    private val mPicked = ArrayList<WallpaperInfo>(4)

    private var mLiveWallpapers: ArrayMap<ComponentName, LiveWallpaperInfo>? = null

    private var connection: DarkWallpaperConnection? = null

    /**
     * Notify wallpaper apply result to ViewModel.
     * **Null** when initialized [DarkModeSettings] and no viewModel attached to it,
     * [DefaultWallpaperSetterCallback] will destroy helper once job is finished.
     * */
    private var viewModelCallback: SetWallpaperCallback? = null

    suspend fun loadPreviewWallpapers(): List<WallpaperInfo> {
        if (mPicked.isNotEmpty()) return mPicked

        val start = System.currentTimeMillis()
        return withContext(Dispatchers.Main) {
            val persisted: List<WallpaperInfo>? = readJson()
            ensureActive()
            if (persisted != null) {
                mPicked.addAll(persisted)
                mPersisted = persisted.toTypedArray()
            } else {
                // Dark mode OFF, use wallpaper from system
                clearPicked()
            }
            val end = System.currentTimeMillis()
            Timber.d("%s DarkWallpaper, time cost: %sms.",
                    if (persisted == null) "No" else "Load", end - start)
            return@withContext mPicked
        }
    }

    private suspend fun readJson(): List<WallpaperInfo>? {
        var persisted: ArrayList<WallpaperInfo>? = null

        for (type in WallpaperType.values()) {
            yield()
            val json = mPreference.getString(type.name, null) ?: break
            val jsonWallpaper = Wallpaper.fromJson(json)
            val wallpaper: WallpaperInfo = if (jsonWallpaper.liveWallpaper) {
                createLiveWallpaper(jsonWallpaper) ?: break
            } else {
                DarkWallpaperInfo(jsonWallpaper.id)
            }
            if (persisted == null) persisted = ArrayList(4)
            persisted.add(wallpaper)
        }
        return persisted
    }

    private suspend fun createLiveWallpaper(wallpaper: Wallpaper): LiveWallpaperInfo? {
        val component = LiveWallpaperInfo.fromJson(wallpaper.id)
        val liveWallpaper = getLiveWallpapers()[component]
        if (liveWallpaper == null) {
            // TODO make a stub wallpaper
            Timber.e("LiveWallpaper %s uninstalled!", component)
        }

        return liveWallpaper
    }

    fun onBoot(darkMode: Boolean) = onAlarm(darkMode)

    fun onAlarm(darkMode: Boolean) {
        if (mPreference.getString(DARK_HOME.name, null) == null) {
            Timber.v("Dark Wallpapers not set, abort.")
            return
        }

        if (mPreference.getBoolean(KEY_LAST_SETTING_SUCCEED, true)) {
            GlobalScope.launch(Dispatchers.Main) {
                applyWallpaper(darkMode)
            }
        } else {
            Timber.v("Error occurred last time, abort")
        }
    }

    /**
     * Apply persisted wallpapers to device
     *
     * @param darkMode Whether apply dark wallpaper or light wallpaper
     * */
    @VisibleForTesting
    suspend fun applyWallpaper(darkMode: Boolean) {
        val wallpapers: List<WallpaperInfo>? = readJson()
        if (wallpapers == null) {
            Timber.e("Error while getting persisted wallpapers, abort.")
            return
        }
        val index = if (darkMode) DARK_HOME.ordinal else HOME.ordinal
        val callback = DefaultWallpaperSetterCallback()
        val home = wallpapers[index]

        if (home is LiveWallpaperInfo) {
            Timber.d("Setting LiveWallpaper id: %s.", home.wallpaperId)
            applyLiveWallpaper(home, callback)
        } else {
            val lock = wallpapers[index + 1] as DarkWallpaperInfo
            Timber.d("Applying Wallpaper, homeId: %s, lockId: %s.", home.wallpaperId, lock.wallpaperId)
            if (home == lock) {
                mSetter.setDarkWallpapers(mContext, lock, null, callback)
            } else {
                mSetter.setDarkWallpapers(mContext, home as DarkWallpaperInfo, lock, callback)
            }
        }
    }

    @VisibleForTesting
    fun applyLiveWallpaper(wallpaper: LiveWallpaperInfo, callback: SetWallpaperCallback) {
        when (val status = ShizukuApi.checkShizuku(mContext!!)) {

            ShizukuStatus.AVAILABLE -> mSetter.setCurrentLiveWallpaper(wallpaper, callback)

            ShizukuStatus.DEAD -> {
                connection = DarkWallpaperConnection(callback)
                with(mContext!!) {
                    val intent = Intent(this, DarkLiveWallpaperService::class.java)
                    intent.putExtra(DarkLiveWallpaperService.ARG_TARGET_WALLPAPER, wallpaper)
                    bindService(intent, connection!!, BIND_AUTO_CREATE)
                    startForegroundService(intent)
                }
            }

            else -> callback.onError(IllegalStateException("Unable connect to Shizuku: $status."))
        }
    }

    /**
     * Temporary store this [CroppedWallpaperInfo] to picked wallpaper list.
     *
     * @param dark 'True' If this wallpaper is picked for dark mode.
     *
     * @return Picked Wallpaper pair to update UI, first one is Home screen, second one is Lock screen.
     * */
    fun pickCroppedWallpaper(wallpaper: CroppedWallpaperInfo, dark: Boolean): Pair<WallpaperInfo, WallpaperInfo> {
        val index = if (dark) DARK_HOME.ordinal else HOME.ordinal
        val destination = wallpaper.destination
        val oldHome = mPicked[index]

        if (destination == DEST_HOME_SCREEN || destination == DEST_BOTH) {
            mPicked[index] = wallpaper
        }

        if (destination == DEST_LOCK_SCREEN || destination == DEST_BOTH) {
            if (oldHome is LiveWallpaperInfo && destination != DEST_BOTH) { //double check
                throw IllegalStateException("Old wallpaper is a LiveWallpaper! dest: $destination")
            }
            mPicked[index + DEST_LOCK_SCREEN] = wallpaper
        }
        return Pair(mPicked[index], mPicked[index + DEST_LOCK_SCREEN])
    }

    /**
     * Temporary store this [LiveWallpaperInfo] to picked wallpaper list, the [Destination] of
     * wallpaper are ignored, since its restricted by [android.app.IWallpaperManager].
     *
     * @param dark 'True' If this wallpaper is picked for dark mode.
     *
     * @return Picked Wallpaper pair to update UI, first one is Home screen, second one is Lock screen.
     *
     * @see DarkWallpaperHelper.mPicked
     * @see android.app.IWallpaperManager.setWallpaperComponent
     * */
    fun pickLiveWallpaper(newWallpaper: LiveWallpaperInfo, dark: Boolean): Pair<WallpaperInfo, WallpaperInfo> {
        val index = if (dark) DARK_HOME.ordinal else HOME.ordinal
        mPicked[index] = newWallpaper
        mPicked[index + DEST_LOCK_SCREEN] = newWallpaper
        return Pair(mPicked[index], mPicked[index + DEST_LOCK_SCREEN])
    }

    /**
     * Persist all picked wallpapers in [mPicked] to storage, and map them
     * with [WallpaperType.name]:[Wallpaper] structure in SharedPreferences.
     *
     * @see PersistableWallpaper.persist
     * */
    suspend fun persist(): List<WallpaperInfo> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val jsonList = ArrayList<Wallpaper>(4)
        // replace mPersisted with new arr once finished
        val newWallpaperArr = ArrayList<WallpaperInfo>(4)
        var exception: Exception? = null
        val context = mContext!!

        try {
            val wallpaperRoot: File = getWallpaperFile(context, "Null").parentFile!!
            if (wallpaperRoot.exists().not() && wallpaperRoot.mkdirs().not()) {
                throw IOException("Unable to crate wallpaper dir: $wallpaperRoot")
            }

            for (type in WallpaperType.values()) {
                yield()
                // persist to storage and convert to DarkWallpaper when needed
                when (val newWallpaper = mPicked[type.ordinal]) {
                    is PersistableWallpaper -> {
                        if (newWallpaper.isNew(context)) {
                            newWallpaper.persist(context)
                        }
                        jsonList.add(Wallpaper.fromBitmap(newWallpaper.wallpaperId))
                        newWallpaperArr.add(DarkWallpaperInfo(newWallpaper.wallpaperId))
                    }

                    is LiveWallpaperInfo -> {
                        jsonList.add(Wallpaper.fromLiveWallpaper(newWallpaper))
                        newWallpaperArr.add(newWallpaper)
                    }

                    is DarkWallpaperInfo -> {
                        jsonList.add(Wallpaper.fromBitmap(newWallpaper.wallpaperId))
                        newWallpaperArr.add(newWallpaper)
                    }

                    else -> throw IllegalArgumentException("Illegal type: ${newWallpaper.javaClass}")
                }
            }
        } catch (ignored: CancellationException) {
            exception = ignored
            return@withContext mPicked
        } catch (e: Exception) {
            exception = e
            throw e
        } finally {
            if (exception != null) {
                Timber.d("Clean up while error happened")
                for (picked in mPicked) {
                    if (picked is PersistableWallpaper && picked.isNew(mContext!!).not()) {
                        picked.delete(mContext!!)
                    }
                }
            }
        }

        // clean old wallpapers, following steps are non-cancellable
        if (isDarWallpaperPersisted()) {
            for (type in WallpaperType.values()) {
                val old = mPersisted!![type.ordinal]
                val new = newWallpaperArr[type.ordinal]
                // skip LiveWallpaper or unchanged wallpaper
                if (old is LiveWallpaperInfo || old == new) continue
                if (isDarkWallpaperInUse(old as DarkWallpaperInfo, newWallpaperArr)) continue
                val file = getWallpaperFile(mContext!!, old.wallpaperId)
                // check exists, home & lock screen usually using same wallpaper
                if (file.exists()) {
                    val result = file.delete()
                    Timber.v(
                        "Delete old wallpaper: Type: %s, Id: %s, Succeed: %s.",
                        type.name,
                        old.wallpaperId,
                        result
                    )
                }
            }
        }

        val editor = mPreference.edit()
        for (type in WallpaperType.values()) {
            editor.putString(type.name, jsonList[type.ordinal].toJsonString())
        }
        // reset last saving flag
        editor.remove(KEY_LAST_SETTING_SUCCEED).apply()

        mPersisted = newWallpaperArr.toTypedArray()
        clearPicked()
        val end = System.currentTimeMillis()
        Timber.i("Persistence completed! time cost: %sms", end - start)
        return@withContext mPicked
    }

    /**
     * Clear all picked wallpapers.
     * */
    fun clearPicked() {
        if (mPicked.isNotEmpty()) mPicked.clear()

        if (isDarWallpaperPersisted()) {
            mPicked.addAll(mPersisted!!)
        } else {
            val sysWallpaper = loadWallpaperFromSystem()
            mPicked.add(sysWallpaper.first)
            mPicked.add(sysWallpaper.second)
            mPicked.add(sysWallpaper.first)
            mPicked.add(sysWallpaper.second)
        }
    }

    suspend fun deleteAll(): Pair<WallpaperInfo, WallpaperInfo> = withContext(Dispatchers.IO) {
        val editor = mPreference.edit()
        WallpaperType.values().forEach { type ->
            editor.remove(type.name)
        }
        editor.remove(KEY_LAST_SETTING_SUCCEED).apply()
        try {
            getWallpaperFile(mContext!!, "null").parentFile?.deleteRecursively()
        } catch (e: Exception) {
            Timber.w(e)
        }
        mPersisted = null
        clearPicked()
        return@withContext Pair(mPicked[0], mPicked[1])
    }

    suspend fun getLiveWallpapers(): ArrayMap<ComponentName, LiveWallpaperInfo> = withContext(Dispatchers.IO) {
        if (mLiveWallpapers == null) {
            synchronized(this@DarkWallpaperHelper) {
                if (mLiveWallpapers == null) {
                    val list = LiveWallpaperInfo.getAll(mContext!!, null)
                    val map = ArrayMap<ComponentName, LiveWallpaperInfo>(list.size)
                    for (wallpaper in list) {
                        map[wallpaper.wallpaperComponentName] = wallpaper
                    }
                    mLiveWallpapers = map
                }
            }
        }
        return@withContext mLiveWallpapers!!
    }

    private fun loadWallpaperFromSystem(): Pair<WallpaperInfo, WallpaperInfo> {
        val live = mManager.wallpaperInfo
        if (live != null) {
            val liveWallpaper = LiveWallpaperInfo(live)
            return Pair(liveWallpaper, liveWallpaper)
        }

        val homeId = mManager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        val lockId = mManager.getWallpaperId(WallpaperManager.FLAG_LOCK)
        val homeWallpaper = SystemWallpaperInfo(WallpaperManager.FLAG_SYSTEM, homeId)
        // using same wallpaper
        return if (lockId == -1) {
            Pair(homeWallpaper, homeWallpaper)
        } else {
            val lockWallpaper = SystemWallpaperInfo(WallpaperManager.FLAG_LOCK, lockId)
            Pair(homeWallpaper, lockWallpaper)
        }
    }

    /**
     * Return True if a LiveWallpaper is picked, the upcoming new picked wallpaper's
     * destination will be ignored.
     *
     * @see StandalonePreviewActivity.Companion.startActivity
     * */
    fun isLiveWallpaperPicked(dark: Boolean): Boolean {
        val index = if (dark) DARK_HOME.ordinal else HOME.ordinal
        val picked: WallpaperInfo = mPicked[index]
        return picked is LiveWallpaperInfo
    }

    fun isDarkWallpaperPicked(): Boolean = checkPicked(true)

    fun isLightWallpaperPicked(): Boolean = checkPicked(false)

    private fun checkPicked(dark: Boolean): Boolean {
        val home = if (dark) DARK_HOME.ordinal else HOME.ordinal
        val lock = home + 1

        val persisted = mPersisted
        return if (persisted != null) {
            persisted[home] != mPicked[home] || persisted[lock] != mPicked[lock]
        } else {
            if (mPicked[home] is LiveWallpaperInfo) {
                mManager.wallpaperInfo?.component?.className?.equals(mPicked[home].wallpaperId)?.not()?: true
            } else {
                // live wallpaper not set, check is SystemWallpaper
                mPicked[home] !is SystemWallpaperInfo || mPicked[lock] !is SystemWallpaperInfo
            }
        }
    }

    fun isDarWallpaperPersisted(): Boolean  {
        // array might not initialized, look up in preference
        return mPersisted != null || mPreference.getString(DARK_HOME.name, null) != null
    }

    /**
     * Returns empty list until calling [loadPreviewWallpapers]
     * */
    fun getPickedWallpaperList(): List<WallpaperInfo> = mPicked

    fun isShizukuDismissed(): Boolean = mPreference.getBoolean(KEY_HIDE_SHIZUKU_WARNING, false)

    fun dismissShizuku() {
        mPreference.edit().putBoolean(KEY_HIDE_SHIZUKU_WARNING, true).apply()
    }

    fun setWallpaperCallback(viewModel: DarkWallpaperPickerViewModel?) {
        viewModelCallback = viewModel
    }

    fun isApplyingLiveWallpaper(): Boolean = connection != null

    fun destroy() {
        INSTANCE = null
        viewModelCallback = null
        connection = null
        mContext = null
        mPersisted = null
        mLiveWallpapers?.clear()
        mLiveWallpapers = null
        mPicked.clear()
    }
}