package me.ranko.autodark.ui

import android.app.Application
import android.app.UiModeManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresPermission
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.ObservableField
import androidx.lifecycle.*
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.ranko.autodark.AutoDarkApplication
import me.ranko.autodark.AutoDarkApplication.isComponentEnabled
import me.ranko.autodark.Constant
import me.ranko.autodark.Constant.*
import me.ranko.autodark.R
import me.ranko.autodark.Receivers.DarkModeAlarmReceiver
import me.ranko.autodark.Utils.DarkLocationUtil
import me.ranko.autodark.Utils.DarkTimeUtil
import me.ranko.autodark.Utils.ViewUtil
import me.ranko.autodark.core.DarkModeSettings
import me.ranko.autodark.core.DarkModeSettings.Companion.setForceDark
import me.ranko.autodark.core.DarkModeSettings.Companion.setForceDarkByShizuku
import me.ranko.autodark.core.ShizukuApi
import me.ranko.autodark.core.ShizukuStatus
import me.ranko.autodark.databinding.DialogBottomResstrictedBinding
import timber.log.Timber
import java.time.LocalTime

enum class DarkSwitch {
    ON,
    OFF,
    SHARE,
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

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
    val switch = ObservableField<DarkSwitch>(getSwitchInSP())

    private val _autoMode = MutableLiveData<Boolean>(darkSettings.isAutoMode())
    /**
     * Control the auto mode switch
     * */
    val autoMode: LiveData<Boolean>
        get() = _autoMode

    /**
     * An observable summary text message
     * Allow UI receive messages from the view model
     *
     * @see     hasDelayedMessage
     * */
    val summaryText = ObservableField<Summary>()

    /**
     * A dark mode changes will cause configuration change.
     * Tell UI to get the summary message
     *
     * @see     getDelayedSummary
     * */
    private var hasDelayedMessage: Boolean = false

    /**
     * Action button for user to trigger dark mode manually
     * while showing summary message
     * */
    private val summaryAction by lazy {
        View.OnClickListener {
            val state = darkSettings.isDarkMode() ?: return@OnClickListener
            if (!darkSettings.setDarkMode(state))
                summaryText.set(newSummary(R.string.dark_mode_permission_denied))
        }
    }

    private val _forceDarkStatus = MutableLiveData<Int>()
    /**
     * The progress that indicates the status of fore-dark switching job
     *
     * @see  triggerForceDark
     * @see  JOB_STATUS_SUCCEED
     * @see  JOB_STATUS_FAILED
     * @see  JOB_STATUS_PENDING
     * */
    val forceDarkStatus: LiveData<Int>
        get() = _forceDarkStatus

    private val _forceDarkTile = MutableLiveData<Int>()
    /**
     * Holds a string res id for Update force-dark
     * preference title when Shizuku is available
     *
     * @see    updateForceDarkTitle
     * */
    val forceDarkTile: LiveData<Int>
        get() = _forceDarkTile

    private val _requirePermission = MutableLiveData<Boolean>()
    /**
     * Control permission dialog
     * */
    val requirePermission: LiveData<Boolean>
        get() = _requirePermission

    val isRestricted = !isComponentEnabled(application, DarkModeAlarmReceiver::class.java)

    private var isDialogShowed = false

    /**
     * Called when fab on main activity has been clicked
     * */
    fun onFabClicked() {
        when (switch.get()) {
            DarkSwitch.ON -> triggerMasterSwitch(false)

            DarkSwitch.OFF -> triggerMasterSwitch(true)

            DarkSwitch.SHARE -> AboutFragment.shareApp(mContext)
        }
    }

    /**
     * Turn main switch on or off
     *
     * @see    DarkModeSettings.setAllAlarm
     * @see    DarkModeSettings.cancelAllAlarm
     * */
    private fun triggerMasterSwitch(status: Boolean) {
        if (!PermissionViewModel.checkSecurePermission(mContext)) {
            // start permission activity
            _requirePermission.value = true
            return
        }

        switch.set(if (status) DarkSwitch.ON else DarkSwitch.OFF)

        // delay 260ms to let button animation finish
        viewModelScope.launch {
            delay(260L)
            val adjusted = if (status) {
                darkSettings.setAllAlarm()
            } else {
                darkSettings.cancelAllAlarm()
            }

            if (adjusted) {
                // dark mode has changed
                // prepare delayed summary message
                hasDelayedMessage = true
            } else {
                // show summary message now
                makeTriggeredSummary()?.apply { summaryText.set(this) }
            }
        }

        saveSwitch(status)
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
        return if (switch.get() == DarkSwitch.OFF) {
            // Show dark mode disabled summary
            newSummary(R.string.dark_mode_disabled)
        } else if (darkSettings.isAutoMode()) {
            newSummary(R.string.dark_mode_summary_auto_on)
        } else {
            val time: LocalTime
            val isDarkMode = darkSettings.isDarkMode() ?: return null
            val textRes: Int = if (isDarkMode) {
                time = darkSettings.getEndTime()
                R.string.dark_mode_summary_will_off
            } else {
                time = darkSettings.getStartTime()
                R.string.dark_mode_summary_will_on
            }

            val displayTime = DarkTimeUtil.getDisplayFormattedString(time)
            val actionStr = mContext.getString(R.string.dark_mode_summary_action)
            Summary(mContext.getString(textRes, displayTime), actionStr, summaryAction)
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
                if (old.xor(darkSettings.isDarkMode() ?: false)) {
                    hasDelayedMessage = true
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

    /**
     * Control force-dark mode on/off
     * This experimental feature will be removed on further android version
     *
     * @see     Constant.COMMAND_GET_FORCE_DARK
     * @see     Constant.COMMAND_SET_FORCE_DARK_OFF
     * @see     Constant.COMMAND_SET_FORCE_DARK_ON
     * */
     fun triggerForceDark(enabled: Boolean) = viewModelScope.launch {
        _forceDarkStatus.value = JOB_STATUS_PENDING

        // Use shizuku if available
        val result = if (R.string.pref_force_dark_shizuku == forceDarkTile.value) {
            setForceDarkByShizuku(enabled)
        } else {
            setForceDark(enabled)
        }
        // wait for animation
        delay(1000L)
        // Show force-dark job result
        _forceDarkStatus.value = if (result) JOB_STATUS_SUCCEED else JOB_STATUS_FAILED
    }

    fun onRequirePermissionConsumed() {
        _requirePermission.value = false
    }

    fun updateForceDarkTitle() = viewModelScope.launch {
        _forceDarkStatus.value = JOB_STATUS_PENDING
        if (ShizukuApi.checkShizuku(mContext) == ShizukuStatus.AVAILABLE) {
            _forceDarkTile.value = R.string.pref_force_dark_shizuku
        }
        _forceDarkStatus.value = JOB_STATUS_SUCCEED
    }

    fun getDelayedSummary(): Summary? {
        return if (hasDelayedMessage) {
            hasDelayedMessage = false
            makeTriggeredSummary()
        } else {
            null
        }
    }

    private fun newSummary(@StringRes message: Int) = Summary(mContext.getString(message), null, null)

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
            // workaround on landscape mode
            if (ViewUtil.isLandscape(activity)) {
                val mBehavior = BottomSheetBehavior.from(binding.root.parent as ViewGroup)
                setOnShowListener { mBehavior.peekHeight = binding.root.height }
            }
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
            override fun <T : ViewModel?> create(modelClass: Class<T>): T {
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
            var actionStr: String?,
            /**
             * Action button for snack bar
             * */
            var action: View.OnClickListener?
        )
    }
}