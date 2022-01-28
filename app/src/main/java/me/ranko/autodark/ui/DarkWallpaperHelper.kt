package me.ranko.autodark.ui

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.util.ArrayMap
import android.view.Surface
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import com.android.wallpaper.asset.BuiltInWallpaperAsset
import com.android.wallpaper.asset.FileAsset
import com.android.wallpaper.asset.StreamableAsset
import com.android.wallpaper.model.LiveWallpaperInfo
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.module.WallpaperPersister
import com.android.wallpaper.module.WallpaperPersister.*
import com.android.wallpaper.module.WallpaperSetter
import kotlinx.coroutines.*
import me.ranko.autodark.R
import me.ranko.autodark.Utils.ViewUtil
import me.ranko.autodark.core.*
import me.ranko.autodark.model.*
import me.ranko.autodark.model.PersistableWallpaper.Companion.getWallpaperFile
import me.ranko.autodark.services.DarkLiveWallpaperService
import me.ranko.autodark.services.RotationListenerService
import me.ranko.autodark.ui.WallpaperType.DARK_HOME
import me.ranko.autodark.ui.WallpaperType.HOME
import timber.log.Timber
import java.io.File
import java.io.IOException

enum class WallpaperType {
    HOME, LOCK, DARK_HOME, DARK_LOCK;
}

/**
 * Helper class to manage dark wallpaper
 * */
class DarkWallpaperHelper private constructor(private val mContext: Context) {

    companion object {
        private const val PREFS_FILE_NAME = "dark_wallpaper"

        private const val DEFAULT_BACKUP_FOLDER = "BackupWallpapers"

        private const val KEY_BACKUP_WALLPAPER_HOME = "bk_HOME"
        private const val KEY_BACKUP_WALLPAPER_LOCK = "bk_LOCK"

        private const val KEY_HIDE_SHIZUKU_WARNING = "hideShizuku"
        private const val KEY_LAST_SETTING_SUCCEED = "NoErr"

        private const val KEY_CHECK_ROTATION = "check_orientation"

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
    }

    private inner class DefaultWallpaperSetterCallback : SetWallpaperCallback {

        override fun onSuccess(id: String) {
            Timber.d("Set wallpaper succeed, new id: %s.", id)
            mPreference.edit().putBoolean(KEY_LAST_SETTING_SUCCEED, true).apply()
            viewModelCallback?.onSuccess(id)
            destroy()
        }

        override fun onError(e: Exception?) {
            if (e is CancellationException) {
                Timber.d(e.localizedMessage)
            } else {
                mPreference.edit().putBoolean(KEY_LAST_SETTING_SUCCEED, false).apply()
                if (viewModelCallback == null) super.onError(e)
            }
            viewModelCallback?.onError(e)
            destroy()
        }

        private fun destroy() {
            connection = null
            if (viewModelCallback == null) this@DarkWallpaperHelper.destroy()
        }
    }

    private val mPreference = mContext.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

    private val mManager by lazy { WallpaperManager.getInstance(mContext) }

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

    private var connection: WallpaperSetterConnection? = null

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
            val wallpaper: WallpaperInfo = readJsonByName(type.name) ?: break
            if (persisted == null) persisted = ArrayList(4)
            persisted.add(wallpaper)
        }
        return if (persisted == null || persisted.size != WallpaperType.values().size) null else persisted
    }

    private suspend fun readJsonByName(name: String): WallpaperInfo? {
        val json = mPreference.getString(name, null) ?: return null
        val jsonWallpaper = Wallpaper.fromJson(json)
        return if (jsonWallpaper.liveWallpaper) {
            createLiveWallpaper(jsonWallpaper)
        } else {
            DarkWallpaperInfo(jsonWallpaper.id)
        }
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
            CoroutineScope(Dispatchers.Main).launch {
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
        val wallpapers: List<WallpaperInfo>? = mPersisted?.asList() ?: readJson()
        if (wallpapers == null) {
            Timber.e("Error while getting persisted wallpapers, abort.")
            return
        }

        val index = if (darkMode) DARK_HOME.ordinal else HOME.ordinal
        val callback = DefaultWallpaperSetterCallback()
        val home = wallpapers[index]

        if (home is LiveWallpaperInfo) {
            applyLiveWallpaper(home, callback)
        } else {
            val lock: DarkWallpaperInfo? = (wallpapers[index + 1]).let {
                if (it.wallpaperId == home.wallpaperId) null else it as DarkWallpaperInfo
            }

            Timber.d("Applying Wallpaper, home:%s, lock:%s.", home.wallpaperId, lock?.wallpaperId)
            if (shouldCheckOrientation() && ViewUtil.getRotation(mContext) != Surface.ROTATION_0) {
                Timber.d("Illegal orientation, starting listener service")
                connection = WallpaperSetterConnection(mContext, Pair(home, lock), callback, mSetter)
                RotationListenerService.startForegroundService(mContext, connection!!)
            } else {
                val homeAsset = home.getAsset(mContext) as StreamableAsset
                val lockAsset = lock?.let { it.getAsset(mContext) as StreamableAsset }
                mSetter.setDarkWallpapers(homeAsset, lockAsset, callback)
            }
        }
    }

    private fun applyLiveWallpaper(wallpaper: LiveWallpaperInfo, callback: SetWallpaperCallback) {
        Timber.d("Applying LiveWallpaper id: %s.", wallpaper.wallpaperId)
        when (ShizukuApi.checkShizukuCompat(mContext)) {

            ShizukuStatus.AVAILABLE -> mSetter.setCurrentLiveWallpaper(wallpaper, callback)

            ShizukuStatus.DEAD -> {
                connection = WallpaperSetterConnection(mContext, wallpaper, callback, mSetter)
                DarkLiveWallpaperService.startForegroundService(mContext, wallpaper, connection!!)
            }

            ShizukuStatus.UNAUTHORIZED -> Toast.makeText(mContext, R.string.permission_failed, Toast.LENGTH_SHORT).show()

            ShizukuStatus.NOT_INSTALL -> callback.onError(IllegalStateException("Shizuku uninstalled"))
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

        if (destination == DEST_HOME_SCREEN || destination == DEST_BOTH) {
            mPicked[index] = wallpaper
        }
        if (destination == DEST_LOCK_SCREEN || destination == DEST_BOTH) {
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
        backupIfNeeded(start)
        val jsonList = ArrayList<Wallpaper>(4)
        // replace mPersisted with new arr once finished
        val newWallpaperArr = ArrayList<WallpaperInfo>(4)
        var exception: Exception? = null
        val context = mContext

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
                    if (picked is PersistableWallpaper && picked.isNew(mContext).not()) {
                        picked.delete(mContext)
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
                val file = getWallpaperFile(mContext, old.wallpaperId)
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
     * Backup current [SystemWallpaperInfo] or [LiveWallpaperInfo] to [DEFAULT_BACKUP_FOLDER].
     * Any exceptions are ignored, since backup procedures aren't that important
     *
     * @see KEY_BACKUP_WALLPAPER_HOME
     * @see KEY_BACKUP_WALLPAPER_LOCK
     * @see restoreOriginalWallpaper
     * */
    private suspend fun backupIfNeeded(start: Long) {
        if (isDarWallpaperPersisted()) return

        val sysWallpapers = loadWallpaperFromSystem()
        val home = sysWallpapers.first
        if (home is LiveWallpaperInfo) {
            mPreference.edit()
                    .putString(KEY_BACKUP_WALLPAPER_HOME, Wallpaper.fromLiveWallpaper(home).toJsonString())
                    .remove(KEY_BACKUP_WALLPAPER_LOCK)
                    .apply()
        } else {
            val backupDir = mContext.getFileStreamPath(DEFAULT_BACKUP_FOLDER)
            if (backupDir.exists().not() && backupDir.mkdir().not()) {
                Timber.e(IOException("Unable to crate backup dir: $backupDir"))
                return // ignore it
            }
            val lock = sysWallpapers.second as SystemWallpaperInfo
            var succeed = true
            try {
                (home as SystemWallpaperInfo).export(mContext, File(backupDir, home.wallpaperId))
                if (lock != home) {
                    lock.export(mContext, File(backupDir, lock.wallpaperId))
                }
            } catch (e: Exception) {
                succeed = false
                Timber.e(e, "Unable to backup wallpaper:")
                return
            } finally {
                if (succeed.not()) backupDir.deleteRecursively()
            }

            val editor = mPreference.edit().putString(KEY_BACKUP_WALLPAPER_HOME, Wallpaper.fromBitmap(home.wallpaperId).toJsonString())
            if (lock != home) {
                editor.putString(KEY_BACKUP_WALLPAPER_LOCK, Wallpaper.fromBitmap(lock.wallpaperId).toJsonString())
            } else {
                editor.remove(KEY_BACKUP_WALLPAPER_LOCK)
            }
            editor.apply()
            val end = System.currentTimeMillis()
            Timber.i("Backup completed! time cost: %sms", end - start)
        }
    }

    /**
     * Restore system wallpaper if there is one, any errors are ignored and [DEFAULT_BACKUP_FOLDER]
     * will be deleted whether successful or not
     *
     * @see backupIfNeeded
     * @see cleanRestoreDir
     * */
    private suspend fun restoreOriginalWallpaper(callback: SetWallpaperCallback) {
        val home: WallpaperInfo? = readJsonByName(KEY_BACKUP_WALLPAPER_HOME)
        if (home == null) {
            callback.onError(null)
            return
        }
        val lock: WallpaperInfo? = readJsonByName(KEY_BACKUP_WALLPAPER_LOCK)

        if (home is LiveWallpaperInfo) {
            val status = ShizukuApi.checkShizukuCompat(mContext)
            if (status == ShizukuStatus.AVAILABLE) {
                applyLiveWallpaper(home, callback)
            } else {
                // skip waiting shizuku online
                callback.onError(IllegalStateException("Unable connect to Shizuku: $status."))
            }
        } else {
            val backupDir = mContext.getFileStreamPath(DEFAULT_BACKUP_FOLDER)
            val homeAsset = FileAsset(File(backupDir, home.wallpaperId))

            if (lock == null) {
                mSetter.setDarkWallpapers(homeAsset, null, callback)
            } else {
                val lockAsset = FileAsset(File(backupDir, lock.wallpaperId))
                mSetter.setDarkWallpapers(homeAsset, lockAsset, callback)
            }
        }
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

    suspend fun deleteAll(callback: SetWallpaperCallback) = withContext(Dispatchers.IO) {
        val editor = mPreference.edit()
        WallpaperType.values().forEach { type ->
            editor.remove(type.name)
        }
        editor.remove(KEY_LAST_SETTING_SUCCEED).apply()
        try {
            getWallpaperFile(mContext, "null").parentFile?.deleteRecursively()
        } catch (e: Exception) {
            Timber.w(e)
        }
        mPersisted = null

        restoreOriginalWallpaper(object : SetWallpaperCallback {
            override fun onSuccess(id: String) {
                cleanRestoreDir()
                callback.onSuccess(id)
            }

            override fun onError(e: java.lang.Exception?) {
                cleanRestoreDir()
                callback.onError(e)
            }
        })
    }

    private fun cleanRestoreDir() {
        mPreference.edit().remove(KEY_BACKUP_WALLPAPER_HOME).remove(KEY_BACKUP_WALLPAPER_LOCK).apply()
        clearPicked()
        if (mContext.getFileStreamPath(DEFAULT_BACKUP_FOLDER).deleteRecursively().not()) {
            Timber.w("Unable to delete backup folder")
        }
    }

    suspend fun getLiveWallpapers(): ArrayMap<ComponentName, LiveWallpaperInfo> = withContext(Dispatchers.IO) {
        if (mLiveWallpapers == null) {
            synchronized(this@DarkWallpaperHelper) {
                if (mLiveWallpapers == null) {
                    val list = LiveWallpaperInfo.getAll(mContext, null)
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

        val homeId: Int = mManager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        val lockId: Int = mManager.getWallpaperId(WallpaperManager.FLAG_LOCK)

        val home: WallpaperInfo = loadWallpaperFromSystem(WallpaperManager.FLAG_SYSTEM, homeId)!!
        return if (homeId == lockId || lockId == -1) {
            Pair(home, home)
        } else {
            val lock: WallpaperInfo? = loadWallpaperFromSystem(WallpaperManager.FLAG_LOCK, lockId)
            Pair(home, lock ?: home)
        }
    }

    @SuppressLint("MissingPermission")
    @VisibleForTesting
    private fun loadWallpaperFromSystem(which: Int, id: Int): SystemWallpaperInfo? {
        when (id) {
            BuiltInWallpaperAsset.BUILT_IN_WALLPAPER_ID -> return BuiltInWallpaperInfo()

            -1 -> return null // Lockscreen wallpaper not configured

            else -> {
                val pfd = mManager.getWallpaperFile(which) ?: return BuiltInWallpaperInfo()
                try {
                    pfd.close()
                } catch (ignored: IOException) {
                }
                return SystemWallpaperInfo(which, id)
            }
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

    /**
     * Check screen orientation before setting wallpaper in the background.
     * A workaround for some modified Android that can't set wallpaper correctly.
     *
     * @see applyWallpaper
     * @see RotationListenerService
     * */
    fun shouldCheckOrientation(): Boolean = mPreference.getBoolean(KEY_CHECK_ROTATION, false)

    fun setCheckOrientation(check: Boolean) {
        mPreference.edit().putBoolean(KEY_CHECK_ROTATION, check).apply()
    }

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
        mPersisted = null
        mLiveWallpapers?.clear()
        mLiveWallpapers = null
        mPicked.clear()
    }
}