package me.ranko.autodark.ui

import android.app.Application
import android.os.Build
import androidx.databinding.ObservableField
import androidx.databinding.ObservableInt
import androidx.lifecycle.*
import androidx.viewpager.widget.ViewPager
import com.android.wallpaper.asset.Asset
import com.android.wallpaper.model.LiveWallpaperInfo
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.module.WallpaperPersister.*
import com.android.wallpaper.util.TaskRunner
import kotlinx.coroutines.*
import me.ranko.autodark.R
import me.ranko.autodark.core.DarkModeSettings
import me.ranko.autodark.core.LoadStatus
import me.ranko.autodark.model.CroppedWallpaperInfo
import timber.log.Timber

class DarkWallpaperPickerViewModel(application: Application) : ShizukuViewModel(application),
        ViewPager.OnPageChangeListener, SetWallpaperCallback {

    enum class WallpaperRequest {
        /**
         * Requesting a chooser. Lets user to choose wallpaper's
         * type either [STATIC_WALLPAPER] or [LIVE_WALLPAPER].
         *
         * @see WallpaperCategoryDialog
         * */
        CATEGORY_CHOOSER,

        /**
         * Requesting a restricted chooser, and warning user LiveWallpapers unavailable.
         *
         * @see WallpaperCategoryDialog
         * */
        CATEGORY_RESTRICTED,

        /**
         * User have chosen LiveWallpaper, show LiveWallpaper browser now.
         *
         * @see DarkWallpaperFragment.LiveWallpaperBrowser
         * */
        LIVE_WALLPAPER,

        /**
         * Live wallpaper picked or user pressed back key, UI will close liveWallpaper
         * browser while receiving this request.
         * */
        LIVE_WALLPAPER_DISMISS,

        /**
         * User have chosen StaticWallpaper.
         * */
        STATIC_WALLPAPER,
        STATIC_WALLPAPER_DISMISS,
    }

    private val mApp: Application = application
    private val mHelper = DarkWallpaperHelper.getInstance(application, this)

    /**
     * Store corrupted wallpaper assets here, e.g uninstalled LiveWallpaper.
     * Disable [_applyAvailable] button until user pick a new one.
     *
     * @see hasErrorAsset
     * @see onWallpaperCorrupted
     * */
    private var mErrorAsset: Array<Asset?>? = null

    private val _pickedLightWallpapers = MutableLiveData<Pair<WallpaperInfo, WallpaperInfo>>()
    val pickedLightWallpapers: LiveData<Pair<WallpaperInfo, WallpaperInfo>>
        get() = _pickedLightWallpapers

    private val _pickedDarkWallpapers = MutableLiveData<Pair<WallpaperInfo, WallpaperInfo>>()
    val pickedDarkWallpapers: LiveData<Pair<WallpaperInfo, WallpaperInfo>>
        get() = _pickedDarkWallpapers

    private var refreshWallpaperJob: Job? = null

    private val _applyAvailable = MutableLiveData(false)
    val applyButtonAvailable: LiveData<Boolean>
        get() = _applyAvailable

    private val _clearAvailable = MutableLiveData(false)
    val clearButtonAvailable: LiveData<Boolean>
        get() = _clearAvailable

    private val _deleteAvailable = MutableLiveData(false)
    val deleteAvailable: LiveData<Boolean>
        get() = _deleteAvailable

    private val _loadingStatus = MutableLiveData<Int>()
    val loadStatus: LiveData<Int>
        get() = _loadingStatus

    val loadingText = ObservableField<String>()

    private var delayedMessage: Int? = null
    val message = ObservableInt()

    /**
     * Notify UI to show permission request view
     * */
    private val _requestPermissions = MutableLiveData<Boolean>()
    val requestPermissions: LiveData<Boolean>
        get() = _requestPermissions

    private val _wallpaperPickRequest = MutableLiveData<WallpaperRequest>()
    val wallpaperPickRequest: LiveData<WallpaperRequest>
        get() = _wallpaperPickRequest

    private var darkModeSelected = false

    private val wallpaperText = application.getString(R.string.pick_wallpaper, application.getString(R.string.pick_wallpaper_type_normal))
    private val darkWallpaperText = application.getString(R.string.pick_wallpaper, application.getString(R.string.pick_wallpaper_type_dark))
    val pickerButtonText = ObservableField(wallpaperText)

    private var exception: Exception? = null

    init {
        _requestPermissions.value = DarkWallpaperPickerActivity.storageGranted(application).not()

        if (mHelper.isApplyingLiveWallpaper()) {
            _loadingStatus.value = LoadStatus.START
            loadingText.set(mApp.getString(R.string.app_loading))
            _deleteAvailable.value = false
        } else {
            _deleteAvailable.value = mHelper.isDarWallpaperPersisted()
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        delayedMessage?.let {
            message.set(it)
            delayedMessage = null
        }
    }

    fun requestCategory() {
        _wallpaperPickRequest.value = when {

            isShizukuAvailable() -> WallpaperRequest.CATEGORY_CHOOSER

            mHelper.isShizukuDismissed() -> WallpaperRequest.STATIC_WALLPAPER

            else -> WallpaperRequest.CATEGORY_RESTRICTED
        }
    }

    fun onCategoryChosen(isLiveWallpaper: Boolean) {
        _wallpaperPickRequest.value = if (isLiveWallpaper) {
            WallpaperRequest.LIVE_WALLPAPER
        } else {
            WallpaperRequest.STATIC_WALLPAPER
        }
    }

    fun onWallpaperPicked(newWallpaper: WallpaperInfo) {
        val target = if (darkModeSelected) _pickedDarkWallpapers else _pickedLightWallpapers

        target.value = when (newWallpaper) {
            is CroppedWallpaperInfo -> {
                onDismissWallpaperPicker()
                removeErrorAsset(newWallpaper.destination)
                mHelper.pickCroppedWallpaper(newWallpaper, darkModeSelected)
            }

            is LiveWallpaperInfo -> {
                onDismissLiveWallpaperPicker()
                removeErrorAsset(DEST_BOTH)
                mHelper.pickLiveWallpaper(newWallpaper, darkModeSelected)
            }

            else -> throw IllegalArgumentException("Wrong type $newWallpaper")
        }
        updateButtonsState()
    }

    fun onDismissWallpaperPicker() {
        _wallpaperPickRequest.value = WallpaperRequest.STATIC_WALLPAPER_DISMISS
    }

    fun onDismissLiveWallpaperPicker() {
        _wallpaperPickRequest.value = WallpaperRequest.LIVE_WALLPAPER_DISMISS
    }

    /**
     * Called when [_applyAvailable] button pressed, persist all picked wallpaper to
     * storage and show loading progress on UI.
     *
     * @see DarkWallpaperHelper.persist
     * @see DarkWallpaperHelper.DefaultWallpaperSetterCallback
     * */
    fun onApplyWallpaperClicked() = viewModelScope.launch(Dispatchers.Main) {
        val loadingStartTime = System.currentTimeMillis()
        _loadingStatus.value = LoadStatus.START
        loadingText.set(mApp.getString(R.string.prepare_wallpaper_progress_message))

        val isDarkMode = DarkModeSettings.getInstance(mApp).isDarkMode() == true
        // skip apply wallpapers on these two rare condition
        val applyWallpaper = when {
            // Dark mode on while dark wallpapers unchanged
            isDarkMode && mHelper.isDarkWallpaperPicked().not() -> false
            // Dark mode off while light wallpapers unchanged
            isDarkMode.not() && mHelper.isLightWallpaperPicked().not() -> false

            else -> true
        }

        try {
            val persisted = mHelper.persist()
            _pickedLightWallpapers.value = Pair(persisted[DEST_HOME_SCREEN], persisted[DEST_LOCK_SCREEN])
            _pickedDarkWallpapers.value = Pair(persisted[DEST_HOME_SCREEN + 2], persisted[DEST_LOCK_SCREEN + 2])
            updateButtonsState()
            if (applyWallpaper) {
                // receive results through callback
                mHelper.onAlarm(isDarkMode)
            } else {
                // Show loadingBar longer
                if (System.currentTimeMillis() - loadingStartTime < 200L) delay(1000L)
                _loadingStatus.value = LoadStatus.SUCCEED
                message.set(R.string.save_wallpaper_success_message)
            }
        } catch (e: Exception) {
            exception = e
            Timber.w(e)
            _loadingStatus.value = LoadStatus.FAILED
        }
    }

    /**
     * Called when successfully applied wallpapers
     *
     * @see onApplyWallpaperClicked
     * @see DarkWallpaperHelper.DefaultWallpaperSetterCallback
     * */
    override fun onSuccess(id: String) {
        if (_loadingStatus.value != LoadStatus.SUCCEED)
            _loadingStatus.value = LoadStatus.SUCCEED
        updateButtonsState()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            delayedMessage = R.string.save_wallpaper_success_message
        } else {
            message.set(R.string.save_wallpaper_success_message)
        }
    }

    /**
     * Called when failed to apply or save wallpapers
     *
     * @see onApplyWallpaperClicked
     * @see DarkWallpaperHelper.DefaultWallpaperSetterCallback
     * */
    override fun onError(e: java.lang.Exception?) {
        Timber.w(e, "onApplyWallpaper: failed to apply wallpapers")
        exception = e
        _loadingStatus.value = LoadStatus.FAILED
    }

    fun onWallpaperCorrupted(asset: Asset) {
        val picked = mHelper.getPickedWallpaperList()
        if (mErrorAsset == null) {
            mErrorAsset = arrayOfNulls(4)
        }

        for (i in picked.indices) {
            val wallpaperInfo = picked[i]
            val pickedAsset = if (wallpaperInfo is LiveWallpaperInfo) {
                wallpaperInfo.getThumbAsset(mApp)
            } else {
                wallpaperInfo.getAsset(mApp)
            }

            if (pickedAsset == asset) {
                mErrorAsset!![i] = asset
            }
        }
        _applyAvailable.value = false
    }

    fun isErrorAssetReported(errorAsset: Asset): Boolean {
        val errAssets = mErrorAsset ?: return false
        for (i in errAssets.indices) {
            if (errAssets[i] != null && errAssets[i] == errorAsset) return true
        }
        return false
    }

    private fun removeErrorAsset(@Destination dest: Int) {
        if (hasErrorAsset().not()) return

        val index = if (darkModeSelected) WallpaperType.DARK_HOME.ordinal else WallpaperType.HOME.ordinal
        if (dest == DEST_HOME_SCREEN || dest == DEST_BOTH) {
            mErrorAsset!![index] = null
        }
        if (dest == DEST_LOCK_SCREEN || dest == DEST_BOTH) {
            mErrorAsset!![index + 1] = null
        }
        if (hasErrorAsset().not()) {
            mErrorAsset = null
        }
    }

    fun deleteAll() = viewModelScope.launch(Dispatchers.Main) {
        val start = System.currentTimeMillis()
        _loadingStatus.value = LoadStatus.START
        _deleteAvailable.value = false
        loadingText.set(mApp.getString(R.string.delete_wallpapers, mApp.getString(R.string.pref_dark_wallpaper_title)))
        mHelper.deleteAll(object : SetWallpaperCallback {
            override fun onSuccess(id: String) {
                onWallpaperDeleted(start, true)
            }

            override fun onError(e: java.lang.Exception?) {
                onWallpaperDeleted(start, false) // ignore error
            }
        })
    }

    private fun onWallpaperDeleted(start: Long, restored: Boolean) = viewModelScope.launch(Dispatchers.Main) {
        val cost = System.currentTimeMillis() - start
        Timber.i("DeleteAll time cost: %s, succeed: %s", cost, restored)
        if (cost < 1000L) delay(2000L)
        refreshWallpaperPreview()
        _loadingStatus.value = LoadStatus.SUCCEED
        message.set(R.string.app_success)
        updateButtonsState()
    }

    fun refreshWallpaperPreview() {
        refreshWallpaperJob?.cancel()

        refreshWallpaperJob = viewModelScope.launch(Dispatchers.Main) {
            val wallpapers = mHelper.loadPreviewWallpapers()
            checkMigrate()
            _pickedLightWallpapers.value = Pair(wallpapers[DEST_HOME_SCREEN], wallpapers[DEST_LOCK_SCREEN])
            _pickedDarkWallpapers.value = Pair(wallpapers[DEST_HOME_SCREEN + 2], wallpapers[DEST_LOCK_SCREEN + 2])
            refreshWallpaperJob = null
        }
    }

    /**
     * Check if AutoWallpaper enabled and contains LiveWallpaper but Shizuku not available.
     * This means user are migrating to Sui,
     * or just simply forgot activate Shizuku after reboot.
     * */
    private fun checkMigrate() {
        if (_deleteAvailable.value == true && !isShizukuAvailable() && isLiveWallpaperPicked() && !mHelper.isShizukuDismissed()) {
            _requestPermissions.value = true
        }
    }

    fun onAllPermissionHandled() {
        _requestPermissions.value = false
    }

    fun isNoDestination(): Boolean = mHelper.isLiveWallpaperPicked(darkModeSelected)

    private fun isLiveWallpaperPicked(): Boolean {
        return mHelper.isLiveWallpaperPicked(true) || mHelper.isLiveWallpaperPicked(false)
    }

    fun onClearWallpaperClicked() {
        mHelper.clearPicked()
        updateButtonsState()
        refreshWallpaperPreview()
    }

    fun onDismissShizukuWarring() = mHelper.dismissShizuku()

    override fun onCleared() {
        if (mHelper.isApplyingLiveWallpaper()) {
            mHelper.setWallpaperCallback(null)
        } else {
            mHelper.destroy()
        }
        TaskRunner.shutdown()
        mApp.cacheDir.listFiles()?.forEach {
            //skip glide live wallpaper cache
            if (it.isFile) it.delete()
        }
    }

    private fun updateButtonsState() {
        _clearAvailable.value = mHelper.isLightWallpaperPicked() || mHelper.isDarkWallpaperPicked()

        _deleteAvailable.value = mHelper.isDarWallpaperPersisted()

        _applyAvailable.value = if (hasErrorAsset()) false else _clearAvailable.value!!
    }

    private fun hasErrorAsset(): Boolean {
        val errors = mErrorAsset ?: return false
        for (i in errors.indices) {
            if (errors[i] != null) return true
        }
        return false
    }

    /**
     * update current selected mode and picker button text
     * */
    override fun onPageSelected(position: Int) {
        darkModeSelected = position != 0
        pickerButtonText.set(if (darkModeSelected) darkWallpaperText else wallpaperText)
    }

    override fun onPageScrollStateChanged(state: Int) {
        // no-op
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        // no-op
    }

    fun getLiveWallpapersAsync(): Deferred<List<LiveWallpaperInfo>> = viewModelScope.async {
        val pm = mApp.packageManager
        mHelper.getLiveWallpapers().values.sortedBy { it.wallpaperComponent.loadLabel(pm).toString() }
    }

    fun getException(): Exception? {
        val e = exception ?: return null
        exception = null
        return e
    }

    fun shouldCheckOrientation(): Boolean = mHelper.shouldCheckOrientation()

    fun setCheckOrientation(check: Boolean) = mHelper.setCheckOrientation(check)

    companion object {
        class Factory(private val application: Application) : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(DarkWallpaperPickerViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return DarkWallpaperPickerViewModel(application) as T
                }
                throw IllegalArgumentException("Unable to construct viewModel")
            }
        }
    }
}