package me.ranko.autodark.ui

import android.annotation.SuppressLint
import android.app.Application
import android.app.UiModeManager
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresPermission
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.ObservableField
import androidx.lifecycle.*
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import com.android.wallpaper.util.ScreenSizeCalculator
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.*
import me.ranko.autodark.AutoDarkApplication
import me.ranko.autodark.AutoDarkApplication.isComponentEnabled
import me.ranko.autodark.Constant.*
import me.ranko.autodark.R
import me.ranko.autodark.receivers.DarkModeAlarmReceiver
import me.ranko.autodark.Utils.DarkLocationUtil
import me.ranko.autodark.Utils.DarkTimeUtil
import me.ranko.autodark.Utils.ViewUtil
import me.ranko.autodark.core.DarkModeSettings
import me.ranko.autodark.databinding.DialogBottomResstrictedBinding
import timber.log.Timber

enum class DarkSwitch(val id: Int) {
    ON(1),
    OFF(3),
    SHARE(6),
}

class MainViewModel(application: Application) : AndroidViewModel(application), DefaultLifecycleObserver {

    private val mContext = application

    val darkSettings = DarkModeSettings.getInstance(application)

    private var sp = PreferenceManager.getDefaultSharedPreferences(application)

    /**
     * Control the main switch on/off
     * Set to [DarkSwitch.OFF], all the pending alarm will be canceled.
     *
     * @see     triggerMasterSwitch
     * @see     DarkSwitch
     * */
    val switch = ObservableField(getSwitchInSP())

    private val _autoMode = MutableLiveData(darkSettings.isAutoMode())
    /**
     * Control the auto mode switch
     * */
    val autoMode: LiveData<Boolean>
        get() = _autoMode

    /**
     * An observable summary text message
     * Allow UI receive messages from the view model
     * */
    val summaryText = ObservableField<Summary>()

    /**
     * A dark mode or wallpaper changes will cause configuration change.
     * Update summary message on [onResume]
     * */
    private var delayedSummary: Summary? = null

    /**
     * Action button for user to trigger dark mode manually
     * while showing summary message
     * */
    private val summaryAction by lazy(LazyThreadSafetyMode.NONE) {
        View.OnClickListener {
            val isDarkMode = darkSettings.isDarkMode() ?: return@OnClickListener
            if (!darkSettings.setDarkMode(isDarkMode.not()))
                summaryText.set(newSummary(R.string.dark_mode_permission_denied))
        }
    }

    private val _requirePermission = MutableLiveData<Boolean>()
    /**
     * Control permission dialog
     * */
    val requirePermission: LiveData<Boolean>
        get() = _requirePermission

    val isRestricted:Boolean by lazy(LazyThreadSafetyMode.NONE) {!isComponentEnabled(application, DarkModeAlarmReceiver::class.java) }

    private var isDialogShowed = false

    /**
     * Called when fab on main activity has been clicked
     * */
    fun onFabClicked() = when (switch.get() as DarkSwitch) {
        DarkSwitch.ON -> triggerMasterSwitch(false)

        DarkSwitch.OFF -> triggerMasterSwitch(true)

        DarkSwitch.SHARE -> AboutFragment.shareApp(mContext)
    }

    /**
     * Turn main switch on or off
     *
     * @see    DarkModeSettings.setAllAlarm
     * @see    DarkModeSettings.cancelAllAlarm
     * */
    private fun triggerMasterSwitch(status: Boolean) {
        if (!AutoDarkApplication.checkSecurePermission(mContext)) {
            // start permission activity
            _requirePermission.value = true
            return
        }

        switch.set(if (status) DarkSwitch.ON else DarkSwitch.OFF)
        val oldNightMode: Boolean = darkSettings.isDarkMode() ?: false

        // delay 360ms to let button animation finish
        viewModelScope.launch {
            saveSwitch(status)
            delay(360L)
            if (status) {
                darkSettings.setAllAlarm()
            } else {
                darkSettings.cancelAllAlarm()
            }

            if (darkSettings.isDarkMode() != oldNightMode) {
                // change wallpaper too
                val helper = DarkWallpaperHelper.getInstance(mContext, null)
                if (helper.isDarWallpaperPersisted()) {
                    helper.onBoot(oldNightMode.not())
                    // workaround on A12, since activity get
                    // recreated twice by wallpaper and dark mode changes
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        delay(1200L)
                        summaryText.set(makeTriggeredSummary())
                        return@launch
                    }
                }
                delayedSummary = makeTriggeredSummary()
            } else {
                // show summary message now
                makeTriggeredSummary()?.apply { summaryText.set(this) }
            }
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        delayedSummary?.let {
            summaryText.set(it)
            delayedSummary = null
        }
    }

    /**
     * Returns Summary message to show when dark mode triggered
     *
     * @return  A Summary message shows to the user.
     *          **Null** when UIModeManager returned error
     *
     * @see     UiModeManager.getNightMode
     * */
    private fun makeTriggeredSummary(): Summary? {
        when {
            switch.get() == DarkSwitch.OFF -> return newSummary(R.string.dark_mode_disabled)

            darkSettings.isAutoMode() -> return newSummary(R.string.dark_mode_summary_auto_on)

            else -> {
                val isDarkMode = darkSettings.isDarkMode() ?: return null
                val displayTime: String
                val textRes: Int = if (isDarkMode) {
                    displayTime = DarkTimeUtil.getDisplayFormattedString(darkSettings.getEndTime())
                    R.string.dark_mode_summary_will_off
                } else {
                    displayTime = DarkTimeUtil.getDisplayFormattedString(darkSettings.getStartTime())
                    R.string.dark_mode_summary_will_on
                }

                val actionStr = mContext.getString(R.string.dark_mode_summary_action)
                return Summary(mContext.getString(textRes, displayTime), actionStr, summaryAction)
            }
        }
    }

    fun onAboutPageChanged(isShowing: Boolean) {
        if (isShowing) {
            switch.set(DarkSwitch.SHARE)
        } else {
            switch.set(getSwitchInSP())
        }
    }

    /**
     * Called when auto mode is clicked
     * */
    @RequiresPermission(allOf = [android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION])
    fun onAutoModeClicked() = viewModelScope.launch(Dispatchers.Main) {
        val locationUtil = DarkLocationUtil.getInstance(mContext)

        // notify user turn location on
        if (!darkSettings.isAutoMode() && !locationUtil.isEnabled()) {
            summaryText.set(newSummary(R.string.app_location_disabled))
        } else {
            val old = darkSettings.isDarkMode() ?: false
            val result = darkSettings.triggerAutoMode()
            if (result) {
                // send delay message if dark mode changed
                if (old.xor(darkSettings.isDarkMode() == true)) {
                    delayedSummary = makeTriggeredSummary()
                } else {
                    summaryText.set(makeTriggeredSummary())
                }
            } else {
                summaryText.set(newSummary(R.string.app_location_failed))
            }
        }

        // send auto mode status as result
        _autoMode.value = darkSettings.isAutoMode()
    }

    fun onRequirePermissionConsumed() {
        _requirePermission.value = false
    }

    @SuppressLint("MissingPermission")
    fun onLocationPermissionResult(granted: Boolean) {
        if (granted) {
            onAutoModeClicked()
        } else {
            _autoMode.value = granted
        }
        showPermissionSummary(granted)
    }

    fun onSecurePermissionResult() {
        if (AutoDarkApplication.checkSecurePermission(getApplication())) {
            darkSettings.overrideIfNeeded()
            showPermissionSummary(true)
        } else {
            showPermissionSummary(false)
        }
    }

    private fun showPermissionSummary(granted: Boolean) {
        val summary = if (granted) R.string.permission_granted else R.string.permission_failed
        summaryText.set(newSummary(summary))
    }

    fun onForceDarkClicked(preference: SwitchPreference, scope: CoroutineScope) = scope.launch(Dispatchers.Main) {
        val start = System.currentTimeMillis()
        preference.isEnabled = false

        val succeed = DarkModeSettings.setForceDark(preference.isChecked)
        // wait switch animation finish
        if (System.currentTimeMillis() - start < 500L) delay(600L)

        if (!succeed && isActive) {
            preference.isChecked = preference.isChecked.not()
            summaryText.set(newSummary(R.string.root_check_failed))
        }
        preference.isEnabled = true
    }

    private fun newSummary(@StringRes message: Int) = Summary(mContext.getString(message))

    /**
     * Some optimize app or OEM performance boost function can disable boot receiver
     * Notify user if this happened and disable __do not show again__ button.
     *
     * */
    fun getRestrictedDialog(activity: AppCompatActivity): BottomSheetDialog? {
        val silence = sp.getBoolean(SP_RESTRICTED_SILENCE, isDialogShowed)
        if (silence && !isRestricted) return null

        // show only once on normal case
        if(isDialogShowed && !isRestricted) return null

        isDialogShowed = true

        return BottomSheetDialog(activity, R.style.AppTheme_BottomSheetDialogDayNight).apply {
            val binding = DialogBottomResstrictedBinding.inflate(
                LayoutInflater.from(context),
                activity.window!!.decorView.rootView as ViewGroup,
                false
            )

            binding.viewModel = this@MainViewModel

            binding.btnLater.setOnClickListener { dismiss() }

            // add strike font style when restricted
            if (isRestricted) {
                Timber.d("Receiver is disabled!")
                ViewUtil.setStrikeFontStyle(binding.btnShutup, true)
            }

            binding.btnShutup.setOnClickListener {
                sp.edit().putBoolean(SP_RESTRICTED_SILENCE, true).apply()
                dismiss()
            }

            setContentView(binding.root)

            val screenSize = ScreenSizeCalculator.getInstance().getScreenSize(activity)
            val mBehavior = BottomSheetBehavior.from(binding.root.parent as ViewGroup)
            setOnShowListener { mBehavior.peekHeight = screenSize.y }
        }
    }

    private fun getSwitchInSP():DarkSwitch {
        return if (sp.getBoolean(SP_KEY_MASTER_SWITCH, false)) DarkSwitch.ON else DarkSwitch.OFF
    }

    private fun saveSwitch(status: Boolean) {
        sp.edit().putBoolean(SP_KEY_MASTER_SWITCH, status).apply()
    }

    companion object {
        class Factory(private val application: Application) : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return MainViewModel(application) as T
                }
                throw IllegalArgumentException("Unable to construct viewModel")
            }
        }

        /**
         * Summary message to show when dark mode changed
         * */
        data class Summary(
            val message: String,
            val actionStr: String? = null,
            /**
             * Action button for snack bar
             * */
            val action: View.OnClickListener? = null
        )
    }
}