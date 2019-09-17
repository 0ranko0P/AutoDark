package me.ranko.autodark.ui

import android.app.Activity
import android.app.Application
import android.app.UiModeManager
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.ranko.autodark.Constant
import me.ranko.autodark.Constant.*
import me.ranko.autodark.R
import me.ranko.autodark.Utils.DarkTimeUtil
import me.ranko.autodark.Utils.ShellJobUtil
import me.ranko.autodark.core.DarkModeSettings
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
     * <p>
     * Set to Off, all the pending alarm will be canceled.
     * */
    private val _masterSwitch = MutableLiveData<Boolean>()
    val masterSwitch: LiveData<Boolean>
        get() = _masterSwitch

    /**
     * Show summary text message wen main switch triggered
     *
     * @see    onDarkSwitchChanged
     * */
    private val _summaryText = MutableLiveData<String>()
    val summaryText: LiveData<String>
        get() = _summaryText

    /**
     * Progress that indicates change fore-dark job status
     *
     * @see  triggerForceDark
     * @see  JOB_STATUS_SUCCEED
     * @see  JOB_STATUS_FAILED
     * @see  JOB_STATUS_PENDING
     * */
    private val _forceDarkStatus = MutableLiveData<Int>()
    val forceDarkStatus: LiveData<Int>
        get() = _forceDarkStatus

    /**
     * Control permission dialog
     * */
    private val _requireAdb = MutableLiveData<Boolean?>()
    val requireAdb: LiveData<Boolean?>
        get() = _requireAdb

    /**
     * Progress that indicates grant with root permission job status
     *
     * @see  JOB_STATUS_SUCCEED
     * @see  JOB_STATUS_FAILED
     * @see  JOB_STATUS_PENDING
     * */
    private val _sudoJobStatus = MutableLiveData<Int>()
    val sudoJobStatus: LiveData<Int>
        get() = _sudoJobStatus


    private val sudoJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main.plus(sudoJob))

    init {
        _masterSwitch.value = sp.getBoolean(SP_KEY_MASTER_SWITCH, false)

        if (!checkPermissionGranted()) {
            // show dialog if not permission
            _requireAdb.value = true
        }
    }

    fun checkPermissionGranted(): Boolean {
        val permission = getApplication<Application>().checkCallingOrSelfPermission(
            PERMISSION_WRITE_SECURE_SETTINGS
        )
        return PackageManager.PERMISSION_GRANTED == permission
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

        val status = masterSwitch.value!!.xor(true)
        _masterSwitch.value = status

        if (status) {
            darkSettings.setAllAlarm()
        } else {
            darkSettings.cancelAllAlarm()
        }

        sp.edit().putBoolean(SP_KEY_MASTER_SWITCH, status).apply()
        onDarkSwitchChanged()
    }

    /**
     * Update summary when dark mode triggered
     * <p>
     * Show a summary text message to user
     *
     * */
    private fun onDarkSwitchChanged() {
        val context = getApplication<Application>()

        if (!masterSwitch.value!!) { // master switch is off now
            _summaryText.value = context.getString(R.string.dark_mode_disabled)
        } else {
            val time: LocalTime
            val textRes: Int
            when (mUiManager.nightMode) {
                UiModeManager.MODE_NIGHT_NO -> {
                    textRes = R.string.dark_mode_summary_off_start
                    time = darkSettings.getStartTime()
                }

                UiModeManager.MODE_NIGHT_YES, UiModeManager.MODE_NIGHT_AUTO -> {
                    textRes = R.string.dark_mode_summary_on_start
                    time = darkSettings.getEndTime()
                }

                else -> {
                    Timber.e("System ui manager returned error code.")
                    return
                }
            }

            val displayTime = DarkTimeUtil.getDisplayFormattedString(time)
            _summaryText.value = context.getString(textRes, displayTime)
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
    fun triggerForceDark(enabled: Boolean) = uiScope.launch {
        _forceDarkStatus.value = JOB_STATUS_PENDING

        try {
            // Run: set force mode && get force mode
            val setCMD = if (enabled) COMMAND_SET_FORCE_DARK_ON else COMMAND_SET_FORCE_DARK_OFF
            val command = "$setCMD && su -c $COMMAND_GET_FORCE_DARK"

            val nowStatus = ShellJobUtil.runSudoJobForValue(command)!!.trim().toBoolean()
            // Check set job result
            _forceDarkStatus.value =
                if (nowStatus == enabled) JOB_STATUS_SUCCEED else JOB_STATUS_FAILED
        } catch (e: Exception) {
            Timber.e("Error: ${e.localizedMessage}")
            _forceDarkStatus.value = JOB_STATUS_FAILED
        }
    }

    /**
     * User want trigger dark mode manually
     * switch mode now
     * */
    fun setDarkModeManually() {
        if (!_masterSwitch.value!!) {
            triggerMasterSwitch()
        } else {
            val state = mUiManager.nightMode == UiModeManager.MODE_NIGHT_NO
            DarkModeSettings.setDarkMode(mUiManager, getApplication(), state)
        }
    }

    fun setSummaryConsumed() {
        _summaryText.value = null
    }

    fun setAdbConsumed() {
        _requireAdb.value = null
    }

    fun setRootConsumed() {
        _sudoJobStatus.value = -1
    }

    fun grantWithRoot() = uiScope.launch {
        _sudoJobStatus.value = JOB_STATUS_PENDING

        val isRooted: Boolean = ShellJobUtil.runSudoJob(COMMAND_GRANT_ROOT)

        Timber.d("Root job finished, result: %s", isRooted)
        if (isRooted) {
            _sudoJobStatus.value = JOB_STATUS_SUCCEED
            _requireAdb.value = false
        } else {
            _sudoJobStatus.value = JOB_STATUS_FAILED
        }
    }

    override fun onCleared() {
        super.onCleared()
        sudoJob.cancel()
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
