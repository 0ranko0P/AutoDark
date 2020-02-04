package me.ranko.autodark.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.databinding.ObservableInt
import androidx.lifecycle.*
import kotlinx.coroutines.*
import me.ranko.autodark.AutoDarkApplication
import me.ranko.autodark.Constant.*
import me.ranko.autodark.R
import me.ranko.autodark.Utils.ShellJobUtil
import me.ranko.autodark.core.ShizukuApi
import moe.shizuku.api.ShizukuApiConstants
import timber.log.Timber

fun AndroidViewModel.checkPermissionGranted(): Boolean =
    AutoDarkApplication.checkSelfPermission(this, PERMISSION_WRITE_SECURE_SETTINGS)

fun AndroidViewModel.checkShizukuPermission(): Boolean =
    AutoDarkApplication.checkSelfPermission(this, ShizukuApiConstants.PERMISSION)

class PermissionViewModel(application: Application) : AndroidViewModel(application) {

    private val sudoJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main.plus(sudoJob))

    /**
     * Progress that indicates grant with root permission job status
     *
     * @see  JOB_STATUS_SUCCEED
     * @see  JOB_STATUS_FAILED
     * @see  JOB_STATUS_PENDING
     * */
    val sudoJobStatus = ObservableInt()

    val shizukuJobStatus = ObservableInt()

    private val _permissionResult = MutableLiveData<Boolean>()
    val permissionResult: LiveData<Boolean>
        get() = _permissionResult

    fun onAdbCheck() {
        _permissionResult.value = checkPermissionGranted()
    }

    fun grantWithRoot() = uiScope.launch {
        sudoJobStatus.set(JOB_STATUS_PENDING)

        delay(800L) // Show progress longer

        val isRooted: Boolean = try {
            ShellJobUtil.runSudoJob(COMMAND_GRANT_PM)
            true
        } catch (e: Exception) {
            false
        }

        // Notify job completed
        sudoJobStatus.set(if (isRooted) JOB_STATUS_SUCCEED else JOB_STATUS_FAILED)

        // dismiss dialog if rooted
        _permissionResult.value = isRooted
        Timber.d("Root job finished, result: %s", isRooted)
    }

    fun grantWithShizuku()  = uiScope.launch{
        shizukuJobStatus.set(JOB_STATUS_PENDING)

        delay(800L)

        val result = try {
            val isAvailable = ShizukuApi.checkShizuku()
            if (isAvailable) {
                ShizukuApi.runShizukuShell(COMMAND_GRANT_PM)
                true
            }else {
                false
            }
        } catch (e: Throwable) {
            false
        }

        // Notify job completed
        shizukuJobStatus.set(if (result) JOB_STATUS_SUCCEED else JOB_STATUS_FAILED)

        // dismiss dialog if rooted
        _permissionResult.value = result
        Timber.d("Shizuku job finished, result: %s", result)
    }

    override fun onCleared() {
        super.onCleared()
        sudoJob.cancel()
    }

    companion object {
        class Factory(private val application: Application) : ViewModelProvider.Factory {
            override fun <T : ViewModel?> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(PermissionViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return PermissionViewModel(application) as T
                }
                throw IllegalArgumentException("Unable to construct viewModel")
            }
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