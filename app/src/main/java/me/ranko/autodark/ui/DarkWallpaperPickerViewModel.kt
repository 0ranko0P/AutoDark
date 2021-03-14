package me.ranko.autodark.ui

import android.app.Application
import androidx.databinding.ObservableField
import androidx.lifecycle.*
import androidx.viewpager.widget.ViewPager
import com.android.wallpaper.asset.Asset
import com.android.wallpaper.model.LiveWallpaperInfo
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.module.WallpaperPersister.*
import kotlinx.coroutines.*
import me.ranko.autodark.Constant
import me.ranko.autodark.R
import me.ranko.autodark.core.DarkModeSettings
import me.ranko.autodark.core.ShizukuApi
import me.ranko.autodark.core.ShizukuStatus
import me.ranko.autodark.model.CroppedWallpaperInfo
import timber.log.Timber

class DarkWallpaperPickerViewModel(application: Application) : AndroidViewModel(application),
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
         * @see WallpaperCategoryDialog.initShizukuPermissionCard
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
    private val mHelper: DarkWallpaperHelper = DarkWallpaperHelper.getInstance(application, this)

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

    private val _shizukuPermissionGranted by lazy(LazyThreadSafetyMode.NONE) {
        ShizukuApi.checkShizuku(mApp) == ShizukuStatus.AVAILABLE
    }

    private var refreshWallpaperJob: Job? = null

    private val _applyAvailable = MutableLiveData<Boolean>(false)
    val applyButtonAvailable: LiveData<Boolean>
        get() = _applyAvailable

    private val _clearAvailable = MutableLiveData<Boolean>(false)
    val clearButtonAvailable: LiveData<Boolean>
        get() = _clearAvailable

    private val _applyStatus = MutableLiveData<Int>()
    val applyStatus: LiveData<Int>
        get() = _applyStatus

    private val _wallpaperPickRequest = MutableLiveData<WallpaperRequest>()
    val wallpaperPickRequest: LiveData<WallpaperRequest>
        get() = _wallpaperPickRequest

    private var darkModeSelected = false

    private val wallpaperText = application.getString(R.string.pick_wallpaper, application.getString(R.string.pick_wallpaper_type_normal))
    private val darkWallpaperText = application.getString(R.string.pick_wallpaper, application.getString(R.string.pick_wallpaper_type_dark))
    val pickerButtonText = ObservableField<String>(wallpaperText)

    private var exception: Exception? = null

    init {
        if (mHelper.isApplyingLiveWallpaper()) {
            _applyStatus.value = Constant.JOB_STATUS_PENDING
        }
    }

    fun requestCategory() {
        _wallpaperPickRequest.value = when {

            _shizukuPermissionGranted -> WallpaperRequest.CATEGORY_CHOOSER

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
        _applyStatus.value = Constant.JOB_STATUS_PENDING

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
                _applyStatus.value = Constant.JOB_STATUS_SUCCEED
            }
        } catch (e: Exception) {
            exception = e
            Timber.w(e)
            _applyStatus.value = Constant.JOB_STATUS_FAILED
        }
    }

    /**
     * Called when successfully applied wallpapers
     *
     * @see onApplyWallpaperClicked
     * @see DarkWallpaperHelper.DefaultWallpaperSetterCallback
     * */
    override fun onSuccess(id: String) {
        if (_applyStatus.value != Constant.JOB_STATUS_SUCCEED)
            _applyStatus.value = Constant.JOB_STATUS_SUCCEED
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
        _applyStatus.value = Constant.JOB_STATUS_FAILED
    }

    fun onWallpaperCorrupted(asset: Asset) {
        val picked = mHelper.getPickedWallpaperList()
        if (mErrorAsset == null) {
            mErrorAsset = arrayOfNulls(4)
        }

        for (i in picked.indices) {
            val pickedAsset = picked[i].getAsset(mApp)
            if (pickedAsset == asset) {
                mErrorAsset!![i] = asset
            }
        }
        _applyAvailable.value = false
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

    fun refreshWallpaperPreview() {
        if (refreshWallpaperJob != null) return

        refreshWallpaperJob = viewModelScope.launch {
            val wallpapers = mHelper.loadPreviewWallpapers()
            _pickedLightWallpapers.value = Pair(wallpapers[DEST_HOME_SCREEN], wallpapers[DEST_LOCK_SCREEN])
            _pickedDarkWallpapers.value = Pair(wallpapers[DEST_HOME_SCREEN + 2], wallpapers[DEST_LOCK_SCREEN + 2])
            refreshWallpaperJob = null
        }
    }

    fun isLiveWallpaperPicked(): Boolean = mHelper.isLiveWallpaperPicked(darkModeSelected)

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
        mApp.cacheDir.listFiles()?.forEach {
            //skip glide live wallpaper cache
            if (it.isFile) it.delete()
        }
    }

    private fun updateButtonsState() {
        _clearAvailable.value = mHelper.isLightWallpaperPicked() || mHelper.isDarkWallpaperPicked()

        when {
            hasErrorAsset() -> _applyAvailable.value = false

            mHelper.isDarWallpaperPersisted() -> _applyAvailable.value = _clearAvailable.value!!

            else -> _applyAvailable.value = mHelper.isDarkWallpaperPicked()
        }
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

    fun getLiveWallpapersAsync(): Deferred<List<LiveWallpaperInfo>> {
        return viewModelScope.async { mHelper.getLiveWallpapers().values.toList() }
    }

    fun getException(): Exception? {
        val e = exception ?: return null
        exception = null
        return e
    }

    companion object {
        class Factory(private val application: Application) : ViewModelProvider.Factory {
            override fun <T : ViewModel?> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(DarkWallpaperPickerViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return DarkWallpaperPickerViewModel(application) as T
                }
                throw IllegalArgumentException("Unable to construct viewModel")
            }
        }
    }
}