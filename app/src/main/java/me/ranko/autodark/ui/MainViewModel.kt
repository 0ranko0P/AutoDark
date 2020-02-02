package me.ranko.autodark.ui

import android.app.Activity
import android.app.Application
import android.app.UiModeManager
import android.view.View
import androidx.core.content.ContextCompat.getSystemService
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import androidx.lifecycle.*
import kotlinx.coroutines.*
import me.ranko.autodark.Constant
import me.ranko.autodark.Constant.*
import me.ranko.autodark.R
import me.ranko.autodark.Utils.DarkTimeUtil
import me.ranko.autodark.core.DarkModeSettings
import me.ranko.autodark.core.DarkModeSettings.Companion.setForceDark
import me.ranko.autodark.core.DarkModeSettings.Companion.setForceDarkByShizuku
import me.ranko.autodark.core.ShizukuApi
import timber.log.Timber
import java.time.LocalTime

const val SP_MAIN_FILE_NAME = "audoDark"
const val SP_KEY_MASTER_SWITCH = "switch"

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val darkSettings = DarkModeSettings(application)

    private var sp = application.getSharedPreferences(SP_MAIN_FILE_NAME, Activity.MODE_PRIVATE)

    private var mUiManager: UiModeManager =
        getSystemService(getApplication(), UiModeManager::class.java)!!

    /**
     * Control the main switch on/off
     * Set to *false*, all the pending alarm will be canceled.
     *
     * @see     triggerMasterSwitch
     * */
    val switch = ObservableBoolean(false)

    /**
     * Show summary text message when main switch triggered
     *
     * @see    makeSummary
     * */
    val summaryText = ObservableField<Summary>()

    /**
     * A dark mode changes will cause configuration change.
     * Tell UI to get the summary message
     *
     * @see     getDelayedSummary
     * */
    private var hasDelayedMessage: Boolean = false

    private val summaryAction = View.OnClickListener { setDarkModeManually() }

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

    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main.plus(job))

    init {
        switch.set(sp.getBoolean(SP_KEY_MASTER_SWITCH, false))
    }

    /**
     * Turn main switch on or off
     *
     * @see    DarkModeSettings.setAllAlarm
     * @see    DarkModeSettings.cancelAllAlarm
     * */
    fun triggerMasterSwitch() {
        if (!checkPermissionGranted()) {
            _requirePermission.value = true
            return
        }

        val status = !switch.get()
        switch.set(status)

        // delay 260ms to let button animation finish
        uiScope.launch {
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
                summaryText.set(makeSummary())
            }
        }

        sp.edit().putBoolean(SP_KEY_MASTER_SWITCH, status).apply()
    }

    /**
     * Summary message to show when dark mode triggered
     * */
    private fun makeSummary(): Summary? {
        val context = getApplication<Application>()

        return if (!switch.get()) {
            // Show dark mode disabled summary
            Summary(context.getString(R.string.dark_mode_disabled), null, null)
        } else {
            val time: LocalTime
            val textRes: Int = when (mUiManager.nightMode) {
                UiModeManager.MODE_NIGHT_NO -> {
                    time = darkSettings.getStartTime()
                    R.string.dark_mode_summary_will_on
                }

                UiModeManager.MODE_NIGHT_YES, UiModeManager.MODE_NIGHT_AUTO -> {
                    time = darkSettings.getEndTime()
                    R.string.dark_mode_summary_will_off
                }

                else -> {
                    Timber.e("System ui manager returned error code.")
                    return null
                }
            }

            val displayTime = DarkTimeUtil.getDisplayFormattedString(time)
            val actionStr = context.getString(R.string.dark_mode_summary_action)
            Summary(context.getString(textRes, displayTime), actionStr, summaryAction)
        }
    }

    /**
     * Control force-dark mode on/off
     * This experimental feature will be removed on further android version
     *
     * @see Constant.COMMAND_GET_FORCE_DARK
     * @see Constant.COMMAND_SET_FORCE_DARK_OFF
     * @see Constant.COMMAND_SET_FORCE_DARK_ON
     * */
    suspend fun triggerForceDark(enabled: Boolean) = uiScope.launch {
        _forceDarkStatus.value = JOB_STATUS_PENDING

        withContext(Dispatchers.Main) {
            // Use shizuku if available
            val result = if (R.string.pref_force_dark_shizuku == forceDarkTile.value) {
                setForceDarkByShizuku(enabled)
            } else {
                setForceDark(enabled)
            }
            // Show force-dark job result
            _forceDarkStatus.value = if (result) JOB_STATUS_SUCCEED else JOB_STATUS_FAILED
        }
    }

    /**
     * User want trigger dark mode manually
     * switch mode now
     * */
    fun setDarkModeManually() {
        if (!switch.get()) {
            triggerMasterSwitch()
        } else {
            val state = mUiManager.nightMode == UiModeManager.MODE_NIGHT_NO
            DarkModeSettings.setDarkMode(mUiManager, getApplication(), state)
        }
    }

    fun onRequireAdbConsumed() {
        _requirePermission.postValue(false)
    }

    fun updateForceDarkTitle() = uiScope.launch {
        withContext(Dispatchers.Main) {
            _forceDarkStatus.value = JOB_STATUS_PENDING

            if (ShizukuApi.checkShizuku() && checkShizukuPermission()) {
                _forceDarkTile.value = R.string.pref_force_dark_shizuku
            }
            _forceDarkStatus.value = JOB_STATUS_SUCCEED
        }
    }

    fun getDelayedSummary(): Summary? {
        return if (hasDelayedMessage) {
            hasDelayedMessage = false
            makeSummary()
        } else {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        job.cancel()
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