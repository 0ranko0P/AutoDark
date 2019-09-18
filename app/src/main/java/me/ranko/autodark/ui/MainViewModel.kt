package me.ranko.autodark.ui

import android.app.Activity
import android.app.Application
import android.app.UiModeManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat.getSystemService
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import androidx.databinding.ObservableInt
import androidx.lifecycle.*
import kotlinx.coroutines.*
import me.ranko.autodark.Constant
import me.ranko.autodark.Constant.*
import me.ranko.autodark.Exception.CommandExecuteError
import me.ranko.autodark.R
import me.ranko.autodark.Utils.DarkTimeUtil
import me.ranko.autodark.Utils.ShellJobUtil
import me.ranko.autodark.core.DarkModeSettings
import timber.log.Timber
import java.time.LocalTime

const val SP_MAIN_FILE_NAME = "audoDark"
const val SP_KEY_MASTER_SWITCH = "switch"

/**
 * Magic happens on my phone 100% reproduce,  0% on emulator
 * SP_KEY_MASTER_SWITCH returned true on first init, use a flag to fix it
 * */
const val SP_KEY_MASTER_SWITCH_FIRST_INIT = "IS_FIRST_INIT"

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
    val switch = ObservableBoolean(false)

    /**
     * Show summary text message wen main switch triggered
     *
     * @see    onDarkSwitchChanged
     * */
    val summaryText = ObservableField<String>()

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
    val sudoJobStatus = ObservableInt()

    private val sudoJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main.plus(sudoJob))

    init {
        // fix focking magic
        // when first install force set master switch to false
        if (sp.getInt(SP_KEY_MASTER_SWITCH_FIRST_INIT, -1) == -1) {
            sp.edit()
                .putInt(SP_KEY_MASTER_SWITCH_FIRST_INIT, 128)
                .putBoolean(SP_KEY_MASTER_SWITCH, false)
                .apply()
        }

        switch.set(sp.getBoolean(SP_KEY_MASTER_SWITCH, false))

        if (!checkPermissionGranted()) {
            // show dialog if not permission
            _requireAdb.value = true
        }
    }

    private fun checkPermissionGranted(): Boolean {
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

        val status = !switch.get()
        switch.set(status)

        // delay 260L to let button animation finish
        uiScope.launch {
            delay(260L)
            if (status) {
                darkSettings.setAllAlarm()
            } else {
                darkSettings.cancelAllAlarm()
            }
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

        if (!switch.get()) { // master switch is off now
            sendDelayText(context.getString(R.string.dark_mode_disabled))
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
            sendDelayText(context.getString(textRes, displayTime))
        }
    }

    /**
     * A config change will recreate view
     * delay the summary text a little while
     * */
    private fun sendDelayText(text: String) = uiScope.launch {
        delay(700L)
        summaryText.set( text)
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
            val result = setForceDark(enabled)
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

    fun setAdbConsumed() {
        _requireAdb.value = null
    }

    fun grantWithRoot() = uiScope.launch {
        sudoJobStatus.set(JOB_STATUS_PENDING)

        delay(800L) // Show progress longer

        val isRooted: Boolean = ShellJobUtil.runSudoJob(COMMAND_GRANT_ROOT)

        // Notify job completed
        val jobStatus = if(isRooted) JOB_STATUS_SUCCEED else JOB_STATUS_FAILED
        sudoJobStatus.set(jobStatus)

        // dismiss dialog
        if (isRooted) _requireAdb.value = false
        Timber.d("Root job finished, result: %s", isRooted)
    }

    fun onAdbCheck() {
        if (checkPermissionGranted()) {
            setAdbConsumed()
            _requireAdb.value = false
            Timber.v("Access granted through ADB.")
        } else {
            Toast.makeText(getApplication(), R.string.adb_check_failed, Toast.LENGTH_SHORT).show()
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

        suspend fun setForceDark(enabled: Boolean): Boolean {
            try {
                // Run: set force mode && get force mode
                val setCMD = if (enabled) COMMAND_SET_FORCE_DARK_ON else COMMAND_SET_FORCE_DARK_OFF
                val command = "$setCMD && su -c $COMMAND_GET_FORCE_DARK"

                val nowStatus = ShellJobUtil.runSudoJobForValue(command)!!.trim().toBoolean()
                return nowStatus == enabled
            } catch (e: Exception) {
                Timber.e("Error: ${e.localizedMessage}")
                return false
            }
        }

        /**
         * @return  current force-dark mode status
         *
         * @throws  CommandExecuteError permission denied when
         *          do not have root access.
         *
         * @see     COMMAND_GET_FORCE_DARK
         * */
        @Throws(CommandExecuteError::class)
        suspend fun getForceDark(): Boolean {
            val forceDark = ShellJobUtil.runJobForValue(COMMAND_GET_FORCE_DARK)
            if (forceDark == null || TextUtils.isEmpty(forceDark.trim())) {
                Timber.v("Force-dark settings is untouched")
                return false
            } else {
                return forceDark.trim().toBoolean()
            }
        }

        @JvmStatic
        val copyAdbCommand = View.OnClickListener { v ->
            val clipboard =
                v.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData = ClipData.newPlainText("command", COMMAND_GRANT_ADB)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(v.context, R.string.app_copy_adb, Toast.LENGTH_SHORT).show()
        }

        @JvmStatic
        val shareAdbCommand = View.OnClickListener { v ->
            val sharingIntent = Intent(Intent.ACTION_SEND)
            sharingIntent.type = "text/plain"
            sharingIntent.putExtra(Intent.EXTRA_SUBJECT, "Null")
            sharingIntent.putExtra(Intent.EXTRA_TEXT, COMMAND_GRANT_ADB)
            v.context.startActivity(
                Intent.createChooser(sharingIntent, v.resources.getString(R.string.adb_share_text))
            )
        }
    }
}
