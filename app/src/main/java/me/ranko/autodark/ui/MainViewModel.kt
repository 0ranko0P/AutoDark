package me.ranko.autodark.ui

import android.app.Activity
import android.app.Application
import android.app.UiModeManager
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
     * @see    onDarkSwitchChanged
     * */
    val summaryText = ObservableField<String>()

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

    private val _requireAdb = MutableLiveData<Boolean>()
    /**
     * Control permission dialog
     * */
    val requireAdb: LiveData<Boolean>
        get() = _requireAdb

    private var _delayedMessage: String? = null

    private val job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main.plus(job))

    init {
        switch.set(sp.getBoolean(SP_KEY_MASTER_SWITCH, false))
    }

    /**
     * Turn main switch on or off
     * <p>
     * Set <strong>on</strong> will recreate all the pending alarm
     * <p>
     * Set <strong>off</strong> will cancel all current alarm
     *
     * @see    DarkModeSettings.setAllAlarm
     * @see    DarkModeSettings.cancelAllAlarm
     * */
    fun triggerMasterSwitch() {
        if (!checkPermissionGranted()) {
            _requireAdb.value = true
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
            onDarkSwitchChanged(adjusted)
        }

        sp.edit().putBoolean(SP_KEY_MASTER_SWITCH, status).apply()
    }

    /**
     * Update summary when dark mode triggered
     *
     * Show a summary text message to user
     *
     * @param   adjusted Is dark mode changed or not.
     *          If parse *True*, means dark mode has changed and
     *          a activity recreation will occur.
     *          Snack bar not showing during the recreation.
     *          new summary message will be stored in ViewModel
     *          wait for the activity to get after recreation.
     *
     * @see     sendSummary
     * @see     getDelayedSummary
     * */
    private fun onDarkSwitchChanged(adjusted: Boolean) {
        val context = getApplication<Application>()

        if (!switch.get()) { // master switch is off now
            sendSummary(adjusted, context.getString(R.string.dark_mode_disabled))
        } else {
            val time: LocalTime
            val textRes: Int = when (mUiManager.nightMode) {
                UiModeManager.MODE_NIGHT_NO -> {
                    time = darkSettings.getStartTime()
                    R.string.dark_mode_summary_off_start
                }

                UiModeManager.MODE_NIGHT_YES, UiModeManager.MODE_NIGHT_AUTO -> {
                    time = darkSettings.getEndTime()
                    R.string.dark_mode_summary_on_start
                }

                else -> {
                    Timber.e("System ui manager returned error code.")
                    return
                }
            }

            val displayTime = DarkTimeUtil.getDisplayFormattedString(time)
            sendSummary(adjusted, context.getString(textRes, displayTime))
        }
    }

    private fun sendSummary(delayed: Boolean, text: String) {
        if (delayed) {
            _delayedMessage = text
        } else {
            summaryText.set(text)
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
        _requireAdb.postValue(false)
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

    fun getDelayedSummary(): String? {
        return if (_delayedMessage == null) {
            null
        } else {
            val str = _delayedMessage
            _delayedMessage = null
            str
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
    }
}